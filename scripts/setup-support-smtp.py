#!/usr/bin/env python3
"""Interactive root-only SMTP setup. Existing password enters through getpass only.

No app deployment/restart, no real support data, no secret in argv/logs/repository.
The authenticated probe uses only a synthetic message to the owner's existing inbox.
"""
import datetime
import getpass
import json
import os
import pathlib
import smtplib
import ssl
import subprocess
import sys
import tempfile
from email.message import EmailMessage

SMTP_HOST = "smtp.hostinger.com"
SMTP_USER = "bilgi@pusulaiklimlendirme.store"
SUPPORT_FROM = "destek@pusulaiklimlendirme.store"
TEST_RECIPIENT = "pusulaiklimlendirme.didim@gmail.com"
ENV_FILE = pathlib.Path("/etc/pusula-support-smtp.env")
DROP_IN = pathlib.Path("/etc/systemd/system/pusula-backend.service.d/40-support-smtp.conf")
RESULT_FILE = pathlib.Path("/var/lib/pusula-support-smtp-setup/result.json")


def quoted(value):
    if any(ord(char) < 32 or ord(char) == 127 for char in value):
        raise ValueError("Control characters are not allowed")
    # systemd EnvironmentFile does not perform shell/variable expansion.
    return '"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'


def write_new(path, text, mode):
    if path.exists() or path.is_symlink():
        raise FileExistsError("Existing setup must be reviewed; it will not be overwritten")
    fd, temporary = tempfile.mkstemp(prefix=".support-smtp-", dir=path.parent)
    try:
        os.fchmod(fd, mode)
        with os.fdopen(fd, "w", encoding="utf-8") as output:
            output.write(text)
            output.flush()
            os.fsync(output.fileno())
        # Hard link installs atomically and fails if the target appeared meanwhile.
        os.link(temporary, path)
    finally:
        os.unlink(temporary)


def main():
    if os.geteuid() != 0 or not sys.stdin.isatty():
        raise RuntimeError("Run as root in an interactive terminal; never pipe the password")
    if ENV_FILE.exists() or ENV_FILE.is_symlink() or DROP_IN.exists() or DROP_IN.is_symlink():
        raise FileExistsError("A dedicated SMTP setup already exists; nothing was changed")
    os.umask(0o077)
    print("Pusula SMTP setup: no backend restart and no support activation.", flush=True)
    print("Enter the EXISTING bilgi mailbox password. Input is hidden and not recorded by this script.", flush=True)
    password = getpass.getpass("Existing mailbox password (hidden): ")
    if not password:
        raise ValueError("Empty password")
    quoted(password)  # Validate before any network operation.
    message = EmailMessage()
    message["From"] = SUPPORT_FROM
    message["To"] = TEST_RECIPIENT
    message["Subject"] = "Pusula SMTP setup test - QA-20261017-SMTP1"
    message.set_content("Synthetic SMTP setup verification only. No customer, support ticket or financial data.")
    with smtplib.SMTP(SMTP_HOST, 587, timeout=10) as smtp:
        smtp.ehlo()
        smtp.starttls(context=ssl.create_default_context())
        smtp.ehlo()
        smtp.login(SMTP_USER, password)
        smtp.send_message(message)
    values = {
        "SPRING_MAIL_HOST": SMTP_HOST,
        "SPRING_MAIL_PORT": "587",
        "SPRING_MAIL_USERNAME": SMTP_USER,
        "SPRING_MAIL_PASSWORD": password,
        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH": "true",
        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE": "true",
        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED": "true",
        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_CHECKSERVERIDENTITY": "true",
        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT": "5000",
        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_TIMEOUT": "10000",
        "SPRING_MAIL_PROPERTIES_MAIL_SMTP_WRITETIMEOUT": "10000",
        "APP_SUPPORT_ENABLED": "false",
        "APP_SUPPORT_EMAIL": SUPPORT_FROM,
        "APP_SUPPORT_WEBSITE": "https://www.pusulaiklimlendirme.com/destek",
        "APP_SUPPORT_NOTIFY_EMAIL": SMTP_USER,
        "APP_SUPPORT_MAIL_FROM": SUPPORT_FROM,
    }
    DROP_IN.parent.mkdir(mode=0o755, parents=True, exist_ok=True)
    write_new(ENV_FILE, "# Root-only Pusula support SMTP settings\n" + "".join(f"{key}={quoted(value)}\n" for key, value in values.items()), 0o600)
    try:
        write_new(DROP_IN, "[Service]\nEnvironmentFile=/etc/pusula-support-smtp.env\n", 0o644)
    except Exception:
        ENV_FILE.unlink()  # Only the new dedicated file created by this invocation.
        raise
    subprocess.run(["systemctl", "daemon-reload"], check=True, capture_output=True)
    RESULT_FILE.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    result = {"timeUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(), "smtpAuthentication": True,
              "syntheticMessageAccepted": True, "testReference": "QA-20261017-SMTP1", "credentialMode": "0600 root",
              "backendRestarted": False, "supportEnabled": False, "outboxEndToEndTested": False}
    write_new(RESULT_FILE, json.dumps(result, indent=2) + "\n", 0o600)
    print("SMTP authenticated; synthetic test accepted. Root-only settings installed.")
    print("Backend was NOT restarted. The settings apply at the next approved restart/release.")
    print("Support stays DISABLED. Backend outbox verification still requires the new backend release.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Never print exception text/tracebacks that could contain SMTP/credential details.
        code = getattr(error, "smtp_code", None)
        print(f"Setup did not finish: {type(error).__name__}" + (f" (SMTP {code})" if code else ""), file=sys.stderr)
        sys.exit(1)
