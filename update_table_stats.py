#!/usr/bin/env python3

import pymysql
import pymysql.cursors
import requests
import json
import os
import argparse
from typing import List, Tuple
from contextlib import contextmanager

# --- CLI Arguments ---
parser = argparse.ArgumentParser(description="Update table stats for a selected MySQL client.")
parser.add_argument("--client", required=True, help="Client name (for Slack message)")
parser.add_argument("--host", required=True, help="MySQL host endpoint")
parser.add_argument("--user", required=True, help="MySQL username")
parser.add_argument("--password", required=True, help="MySQL password")
args = parser.parse_args()

# --- Slack Configuration ---
SLACK_WEBHOOK_URL = os.getenv("SLACK_WEBHOOK_URL", "")
SLACK_CHANNEL = "#prod-stats-update-alert"

@contextmanager
def get_connection(host: str, user: str, password: str):
    connection = pymysql.connect(
        host=host,
        user=user,
        password=password,
        charset='utf8mb4',
        autocommit=True,
        cursorclass=pymysql.cursors.DictCursor,
        connect_timeout=10
    )
    try:
        yield connection
    finally:
        connection.close()

def update_table_stats(cursor, client: str, schema: str, table: str) -> bool:
    try:
        cursor.execute(f"ANALYZE NO_WRITE_TO_BINLOG TABLE `{schema}`.`{table}`;")
        print(f"Stats updated for {schema}.{table}")
        return True
    except pymysql.Error as e:
        print(f"Failed to analyze {schema}.{table} for client {client}: {e}")
        return False

def main():
    client = args.client
    host = args.host
    user = args.user
    password = args.password

    updated_tables = []

    try:
        with get_connection(host, user, password) as conn:
            with conn.cursor() as cursor:
                print(f"Connected to {client} at {host} as {user}")

                cursor.execute("""
                    SELECT TABLE_NAME, TABLE_SCHEMA 
                    FROM information_schema.tables 
                    WHERE TABLE_SCHEMA NOT IN ('information_schema', 'performance_schema', 'mysql', 'sys')
                    AND TABLE_TYPE = 'BASE TABLE'
                    ORDER BY TABLE_SCHEMA, TABLE_NAME;
                """)
                tables = cursor.fetchall()

                if not tables:
                    print(f"No base tables found for client: {client}")
                else:
                    for table in tables:
                        schema = table['TABLE_SCHEMA']
                        table_name = table['TABLE_NAME']
                        if update_table_stats(cursor, client, schema, table_name):
                            updated_tables.append(f"{schema}.{table_name}")

    except Exception as e:
        print(f"❌ Failed to process {client}: {e}")

    # --- Slack Notification ---
    if updated_tables:
        text = f"✅ Stats updated for *{client}*.\nTotal tables updated: *{len(updated_tables)}*."
    else:
        text = f"⚠️ No stats updated for *{client}*."

    if SLACK_WEBHOOK_URL:
        try:
            payload = {
                "channel": SLACK_CHANNEL,
                "username": "StatsBot",
                "icon_emoji": ":bar_chart:",
                "text": text
            }
            response = requests.post(
                SLACK_WEBHOOK_URL,
                headers={"Content-Type": "application/json"},
                data=json.dumps(payload)
            )
            response.raise_for_status()
            print("Slack message sent successfully.")
        except Exception as e:
            print(f"Failed to send Slack message: {e}")
    else:
        print("No Slack webhook set; skipping Slack notification.")

if __name__ == "__main__":
    main()
