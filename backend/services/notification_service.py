"""
Notification and Alert Dispatch Service
Prepares interfaces for Email and Firebase Cloud Messaging (FCM) alerts to trusted contacts.
Enforces explicit user consent and dry-run safety modes; never contacts real individuals during testing.
"""

import uuid
import logging
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional

from backend.config import settings
from backend.database.connection import get_firestore_client
from backend.models.schemas import NotificationLog

logger = logging.getLogger("scam_shield.notification")


class NotificationService:
    """
    Manages notifications to trusted contacts during HIGH or CRITICAL threats.
    Never dispatches actual messages unless explicitly configured and consented to by user.
    """

    def __init__(self):
        self.enabled = settings.NOTIFICATION_ENABLED
        self.dry_run = settings.NOTIFICATION_DRY_RUN

    async def notify_trusted_contacts(
        self,
        user_id: str,
        analysis_id: str,
        risk_score: int,
        risk_level: str,
        consent_given: bool = False,
    ) -> List[NotificationLog]:
        """
        Notify verified trusted contacts of a high-risk scam event.

        Safety Rules:
        1. User consent is MANDATORY (`consent_given=True`).
        2. In test/development mode (`dry_run=True`), records are simulated and logged.
        3. Never sends real SMS or emails without active configuration.
        """
        logs: List[NotificationLog] = []

        if not consent_given:
            logger.info(f"Skipping notifications for user '{user_id}': User consent was not granted.")
            return logs

        if risk_level not in ("HIGH", "CRITICAL"):
            logger.info(f"Skipping notifications: Risk level '{risk_level}' is below alert threshold.")
            return logs

        db = get_firestore_client()
        contacts_to_alert = []

        # Fetch trusted contacts from Firestore if database is available
        if db is not None:
            try:
                docs = db.collection("trusted_contacts").where("user_id", "==", user_id).stream()
                for doc in docs:
                    data = doc.to_dict()
                    if data.get("notify_on_high_risk", True):
                        contacts_to_alert.append(data)
            except Exception as exc:
                logger.error(f"Failed to fetch trusted contacts for notification: {exc}")
        else:
            logger.info("Firestore unavailable: Notification query simulated.")

        if not contacts_to_alert:
            logger.info(f"No configured trusted contacts found for user '{user_id}'.")
            return logs

        alert_message = (
            f"[Scam Shield Alert] A potentially fraudulent phone call with {risk_level} risk "
            f"(Score: {risk_score}/100) was detected for user account {user_id}. "
            "Please check on them and advise them not to transfer any funds or share OTPs."
        )

        for contact in contacts_to_alert:
            recipient = contact.get("phone_number") or contact.get("email") or "unknown"
            log_id = str(uuid.uuid4())

            if self.dry_run or not self.enabled:
                # Dry run mode: safe simulation
                logger.info(
                    f"[DRY-RUN SIMULATION] Notification to {recipient} ({contact.get('relationship')}): {alert_message}"
                )
                status_str = "simulated_dry_run"
            else:
                # Placeholder for live transport (e.g., FCM / SendGrid / Twilio)
                # Production transports must be configured with secure environment credentials.
                logger.warning(f"Production transport triggered for {recipient} - delivering alert.")
                status_str = "dispatched"

            log_entry = NotificationLog(
                id=log_id,
                user_id=user_id,
                analysis_id=analysis_id,
                recipient=recipient,
                channel="dry_run_simulator" if self.dry_run else "production_transport",
                status=status_str,
                message=alert_message,
                timestamp=datetime.now(timezone.utc),
            )
            logs.append(log_entry)

            # Persist notification log to Firestore if available
            if db is not None:
                try:
                    db.collection("notifications").document(log_id).set(log_entry.model_dump(mode="json"))
                except Exception as exc:
                    logger.warning(f"Failed to persist notification audit log: {exc}")

        return logs


notification_service = NotificationService()
