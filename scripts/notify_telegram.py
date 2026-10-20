"""Send a consistently formatted GitLab pipeline result to Telegram."""

import html
import json
import os
import urllib.error
import urllib.parse
import urllib.request


def required(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise SystemExit(f"Missing required CI variable: {name}")
    return value


def main() -> None:
    token = required("TELEGRAM_BOT_TOKEN")
    chat_id = required("TELEGRAM_CHAT_ID")
    thread_id = required("TELEGRAM_THREAD_ID")
    status = os.getenv("TELEGRAM_STATUS", "UNKNOWN").strip().upper()
    project = html.escape(os.getenv("CI_PROJECT_NAME", "framework-api"))
    branch = html.escape(os.getenv("CI_COMMIT_REF_NAME", "unknown"))
    commit = html.escape(os.getenv("CI_COMMIT_SHORT_SHA", "unknown"))
    pipeline_url = os.getenv("CI_PIPELINE_URL", "").strip()
    status_icon = "✅" if status == "SUCCESS" else "❌" if status == "FAILED" else "🔔"
    details = (
        f'<a href="{html.escape(pipeline_url, quote=True)}">Open pipeline</a>'
        if pipeline_url
        else "Pipeline URL unavailable"
    )
    message = "\n".join(
        [
            f"{status_icon} <b>Framework CI/CD notification</b>",
            f"Project: <code>{project}</code>",
            "Platform: <b>GitLab</b>",
            f"Status: <b>{html.escape(status)}</b>",
            f"Ref: <code>{branch}</code>",
            f"Commit: <code>{commit}</code>",
            f"Details: {details}",
        ]
    )
    payload = urllib.parse.urlencode(
        {
            "chat_id": chat_id,
            "message_thread_id": thread_id,
            "text": message,
            "parse_mode": "HTML",
            "disable_web_page_preview": "true",
        }
    ).encode("utf-8")
    request = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/sendMessage",
        data=payload,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            result = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise SystemExit(f"Telegram API returned HTTP {error.code}") from None
    except urllib.error.URLError as error:
        raise SystemExit(f"Could not reach Telegram API: {error.reason}") from None
    if not result.get("ok"):
        raise SystemExit("Telegram API rejected the notification")
    print("Telegram notification sent.")


if __name__ == "__main__":
    main()
