#!/usr/bin/env python3
"""
MySQL Table Statistics Updater with Slack Notifications.
Supports dynamic client selection and environment-based credential mapping.
"""

import pymysql
import pymysql.cursors
import requests
import json
import os
import argparse
from typing import List, Tuple
from contextlib import contextmanager

# Parse CLI argument for selected client
parser = argparse.ArgumentParser()
parser.add_argument('--client', required=True, help='Client name: MFM, MFM_REPLICA, MFMCORR')
args = parser.parse_args()

client_key = args.client.upper()

# Credential mapping (from environment variables)
CLIENT_CONFIG = {
    "MFM": (
        "mfm-prod.cluster-cpewz1juqrts.us-east-1.rds.amazonaws.com",
        "mfm_prod_admin",
        os.getenv("DB_PASS_MFM")
    ),
    "MFM_REPLICA": (
        "mfm-prod.cluster-ro-cpewz1juqrts.us-east-1.rds.amazonaws.com",
        "mfm_prod_admin",
        os.getenv("DB_PASS_MFM_REPL")
    ),
    "MFMCORR": (
        "mfmcorr-prod.cluster-cphgt2qz1ifr.us-east-1.rds.amazonaws.com",
        "mfmcorr_prod_adm",
        os.getenv("DB_PASS_MFMCORR")
    ),
}

if client_key not in CLIENT_CONFIG:
    raise Exception(f"Unknown client: {client_key}")

CLIENTS = [(client_key, *CLIENT_CONFIG[client_key])]
SLACK_WEBHOOK_URL = os.getenv("SLACK_WEBHOOK_URL", "https://hooks.slack.com/services/...")
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
    updated_clients_info = []
    
    for client, host, user, password in CLIENTS:
        try:
            with get_connection(host, user, password) as conn:
                with conn.cursor() as cursor:
                    print(f"Connected to client: {client}")
                    cursor.execute("""
                        SELECT TABLE_NAME, TABLE_SCHEMA 
                        FROM information_schema.tables 
                        WHERE TABLE_SCHEMA NOT IN ('information_schema', 'performance_schema', 'mysql', 'sys')
                        AND TABLE_TYPE = 'BASE TABLE'
                        ORDER BY TABLE_SCHEMA, TABLE_NAME;
                    """)
                    tables = cursor.fetchall()

                    if not tables:
                        print(f"No tables found for client {client}")
                        continue
                    
                    stats_updated = False
                    for table in tables:
                        schema = table['TABLE_SCHEMA']
                        table_name = table['TABLE_NAME']
                        if update_table_stats(cursor, client, schema, table_name):
                            stats_updated = True
                    
                    if stats_updated:
                        updated_clients_info.append((client, f"Updated stats for {len(tables)} tables"))
        except pymysql.Error as e:
            print(f"Database error for client {client}: {e}")
        except Exception as e:
            print(f"Unexpected error for client {client}: {e}")

    # Slack Notification
    if updated_clients_info:
        message = {
            "channel": SLACK_CHANNEL,
            "username": "StatsBot",
            "icon_emoji": ":robot_face:",
            "text": "Statistics update completed for:\n" +
                    "\n".join([f"• {client}: {info}" for client, info in updated_clients_info])
        }
    else:
        message = {
            "channel": SLACK_CHANNEL,
            "username": "StatsBot",
            "text": "No statistics updates were performed.",
            "icon_emoji": ":robot_face:"
        }

    try:
        response = requests.post(
            SLACK_WEBHOOK_URL,
            headers={"Content-Type": "application/json"},
            data=json.dumps(message),
            timeout=10
        )
        response.raise_for_status()
        print("Slack notification sent")
    except requests.RequestException as e:
        print(f"Failed to send Slack notification: {e}")

if __name__ == "__main__":
    main()
