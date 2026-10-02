/**
 * Karz Cloud Functions.
 *
 * sendReminders (every 6 hours): EMI reminder fan-out.
 * - Scans collectionGroup("loans") for active loans with nextDueDate within
 *   the next 3 days (or overdue).
 * - Passes: T-3, T-1, T+0, and a T+2 missed-payment check.
 * - dedupeKey guard (emi:{loanId}:{yyyy-MM-dd} in the USER's timezone) so a
 *   retry or overlapping run never double-sends.
 * - Copy is timezone-aware: dates render in the user's IANA timezone from
 *   their profile, never UTC.
 * - FCM goes to every registered token; invalid/expired tokens are pruned
 *   from the user doc.
 * - Each send is recorded in users/{uid}/notifications with sentAt.
 *
 * The function never recomputes amortization. It only reads the
 * client-written schedule/nextDueDate fields.
 */

const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();
const messaging = admin.messaging();

const DAY_MS = 24 * 60 * 60 * 1000;
const THREE_DAYS_MS = 3 * DAY_MS;

/** yyyy-MM-dd in the given IANA timezone. */
function dayKey(epochMs, timeZone) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(epochMs));
}

/** "5 Oct" style date in the user's timezone. */
function shortDate(epochMs, timeZone) {
  return new Intl.DateTimeFormat("en-IN", {
    timeZone,
    day: "numeric",
    month: "short",
  }).format(new Date(epochMs));
}

function money(minor, currency) {
  const code = (currency || "INR").toUpperCase();
  try {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: code,
      maximumFractionDigits: 0,
    }).format(minor / 100);
  } catch (e) {
    return `${code} ${(minor / 100).toLocaleString("en-IN")}`;
  }
}

/** Reminder pass for a loan, or null when no pass applies right now. */
function passFor(dueInDays) {
  if (dueInDays === 3) return "emi_t3";
  if (dueInDays === 1) return "emi_t1";
  if (dueInDays === 0) return "emi_today";
  if (dueInDays <= -2) return "emi_missed";
  return null;
}

function copyFor(pass, loan, timeZone) {
  const amount = money(loan.emiAmountMinor || 0, loan.currency);
  const lender = loan.lenderName || "your lender";
  const due = shortDate(loan.nextDueDate, timeZone);
  switch (pass) {
    case "emi_t3":
      return {
        title: "EMI due in 3 days",
        body: `Your ${amount} EMI to ${lender} is due on ${due}. Pay a little early and stay stress free.`,
      };
    case "emi_t1":
      return {
        title: "EMI due tomorrow",
        body: `Your ${amount} EMI to ${lender} is due tomorrow (${due}). Keep the balance ready.`,
      };
    case "emi_today":
      return {
        title: "EMI due today",
        body: `Your ${amount} EMI to ${lender} is due today. Tap to record your payment.`,
      };
    case "emi_missed":
      return {
        title: "Missed EMI",
        body: `Your ${amount} EMI to ${lender} was due on ${due}. Pay now to avoid late fees.`,
      };
    default:
      return null;
  }
}

const INVALID_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

exports.sendReminders = onSchedule({ schedule: "every 6 hours" }, async () => {
  const nowMs = Date.now();

  let loansSnap;
  try {
    loansSnap = await db
      .collectionGroup("loans")
      .where("status", "==", "ACTIVE")
      .where("nextDueDate", "<=", nowMs + THREE_DAYS_MS)
      .get();
  } catch (e) {
    console.error("sendReminders: loans query failed (missing composite index?)", e);
    return;
  }

  // Overdue loans are included by the <= now+3d bound; the pass filter below
  // decides T-3 / T-1 / T+0 / missed.
  const userCache = new Map();
  async function getUser(uid) {
    if (!userCache.has(uid)) {
      const snap = await db.doc(`users/${uid}`).get();
      userCache.set(uid, snap.exists ? snap.data() : null);
    }
    return userCache.get(uid);
  }

  let sent = 0;
  let skipped = 0;

  for (const loanDoc of loansSnap.docs) {
    try {
      const uid = loanDoc.ref.parent.parent.id;
      const loan = loanDoc.data();
      if (typeof loan.nextDueDate !== "number") {
        skipped++;
        continue;
      }
      const user = await getUser(uid);
      if (!user) {
        skipped++;
        continue;
      }
      if (user.notificationsEnabled === false) {
        skipped++;
        continue;
      }

      const timeZone = user.timezone || "Asia/Kolkata";
      const dueInDays = Math.round((loan.nextDueDate - nowMs) / DAY_MS);
      const pass = passFor(dueInDays);
      if (!pass) {
        skipped++;
        continue;
      }

      const dedupeKey = `emi:${loanDoc.id}:${dayKey(nowMs, timeZone)}`;
      const notifCol = db.collection(`users/${uid}/notifications`);
      const existing = await notifCol.where("dedupeKey", "==", dedupeKey).limit(1).get();
      if (!existing.empty) {
        skipped++;
        continue;
      }

      const copy = copyFor(pass, loan, timeZone);
      if (!copy) {
        skipped++;
        continue;
      }

      const tokenEntries = user.fcmTokens || {};
      const tokens = Object.values(tokenEntries)
        .map((e) => e && e.token)
        .filter((t) => typeof t === "string" && t.length > 0);

      let delivered = 0;
      if (tokens.length > 0) {
        const resp = await messaging.sendEachForMulticast({
          tokens,
          notification: { title: copy.title, body: copy.body },
          data: {
            type: pass,
            loanId: loanDoc.id,
            dedupeKey,
          },
        });
        delivered = resp.successCount;

        const badKeys = [];
        resp.responses.forEach((r, i) => {
          if (!r.success && r.error && INVALID_TOKEN_CODES.has(r.error.code)) {
            const entry = Object.entries(tokenEntries).find(([, e]) => e && e.token === tokens[i]);
            if (entry) badKeys.push(entry[0]);
          }
        });
        if (badKeys.length > 0) {
          const updates = {};
          badKeys.forEach((k) => {
            updates[`fcmTokens.${k}`] = admin.firestore.FieldValue.delete();
          });
          await db.doc(`users/${uid}`).update(updates);
          console.log(`pruned ${badKeys.length} invalid FCM tokens for ${uid}`);
        }
      }

      await notifCol.add({
        type: pass,
        title: copy.title,
        body: copy.body,
        loanId: loanDoc.id,
        dedupeKey,
        scheduledFor: admin.firestore.Timestamp.fromMillis(nowMs),
        sentAt: admin.firestore.FieldValue.serverTimestamp(),
        tokensTargeted: tokens.length,
        tokensDelivered: delivered,
        data: { dueInDays },
      });
      sent++;
    } catch (e) {
      console.error(`sendReminders: failed for loan ${loanDoc.id}`, e);
    }
  }

  console.log(`sendReminders done: sent=${sent} skipped=${skipped}`);
});
