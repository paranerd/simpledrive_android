package org.simpledrive.authenticator;

import android.accounts.Account;
import android.accounts.AccountManager;

import android.content.Context;
import android.os.Bundle;

import org.simpledrive.helper.Connection;
import org.simpledrive.helper.Util;

import java.util.ArrayList;

public class CustomAuthenticator {
    private static AccountManager am;
    private static Account[] aaccount;
    private static int activeAccount = 0;
    private static boolean enabled = false;
    public static int MAX_UNLOCK_ATTEMPTS = 3;
    public static int COOLDOWN_ADD = 5000;

    private static String KEY_TOKEN = "token";
    private static String KEY_SERVER = "server";
    private static String KEY_PIN = "pin";
    private static String KEY_UNLOCK_ATTEMPTS = "unlockAttempts";
    private static String KEY_LAST_UNLOCK_ATTEMPT = "lastUnlockAttempt";
    private static String KEY_LOCKED = "locked";

    public static boolean enable(Context context) {
        am = AccountManager.get(context);
        aaccount = am.getAccountsByType("org.simpledrive");

        if (aaccount.length > 0) {
            enabled = true;
            return true;
        }

        return false;
    }

    public static boolean addAccount(String username, String password, String server, String token) {
        Account account = new Account(username, "org.simpledrive");
        Bundle userdata = new Bundle();
        userdata.putString(KEY_SERVER, server);
        userdata.putString(KEY_TOKEN, token);
        userdata.putString(KEY_UNLOCK_ATTEMPTS, "0");
        userdata.putString(KEY_LAST_UNLOCK_ATTEMPT, "0");
        userdata.putString(KEY_PIN, "");

        Connection.setToken(token);

        return am.addAccountExplicitly(account, password, userdata);
    }

    public static void removeAccount() {
        if (enabled) {
            am.removeAccount(aaccount[activeAccount], null, null, null);
        }

        aaccount = am.getAccountsByType("org.simpledrive");

        if (aaccount.length == 0) {
            enabled = false;
        }
    }

    public static String getUsername() {
        return (enabled && aaccount.length > 0) ? aaccount[activeAccount].name : "";
    }

    public static String getPassword() {
        return (enabled && aaccount.length > 0) ? am.getPassword(aaccount[activeAccount]) : "";
    }

    public static String getServer() {
        return (enabled && aaccount.length > 0) ? am.getUserData(aaccount[activeAccount], KEY_SERVER) : "";
    }

    public static void updateToken(String token) {
        if (enabled) {
            am.setUserData(aaccount[activeAccount], KEY_TOKEN, token);
            Connection.setToken(token);
        }
    }

    public static void lock() {
        if (enabled && hasPIN()) {
            am.setUserData(aaccount[activeAccount], KEY_LOCKED, "1");
        }
    }

    public static boolean isLocked() {
        if (enabled) {
            String locked = am.getUserData(aaccount[activeAccount], KEY_LOCKED);
            return (locked != null && locked.equals("1"));
        }
        return false;
    }

    public static boolean unlock(String enteredPin) {
        if (enabled) {
            String pin = am.getUserData(aaccount[activeAccount], KEY_PIN);

            if (getCooldown() > 0) {
                return false;
            }

            if (pin.equals("")|| pin.equals(enteredPin)) {
                resetUnlockCounter();
                am.setUserData(aaccount[activeAccount], KEY_LOCKED, "0");
                return true;
            } else {
                incrementUnlockCounter();
            }
        }

        return false;
    }

    public static long getRemainingUnlockAttempts() {
        return (enabled) ? MAX_UNLOCK_ATTEMPTS - Long.parseLong(am.getUserData(aaccount[activeAccount], KEY_UNLOCK_ATTEMPTS)) : 0;
    }

    public static long getCooldown() {
        if (enabled) {
            Long unlockAttempts = Long.parseLong(am.getUserData(aaccount[activeAccount], KEY_UNLOCK_ATTEMPTS));
            Long lastUnlockAttempt = Long.parseLong(am.getUserData(aaccount[activeAccount], KEY_LAST_UNLOCK_ATTEMPT));
            return (lastUnlockAttempt + (unlockAttempts - (MAX_UNLOCK_ATTEMPTS - 1)) * COOLDOWN_ADD) - Util.getTimestamp();
        }
        return 0;
    }

    public static void resetUnlockCounter() {
        if (enabled) {
            am.setUserData(aaccount[activeAccount], KEY_UNLOCK_ATTEMPTS, "0");
        }
    }

    public static void incrementUnlockCounter() {
        if (enabled) {
            Long unlockAttempts = Long.parseLong(am.getUserData(aaccount[activeAccount], KEY_UNLOCK_ATTEMPTS));
            unlockAttempts++;
            am.setUserData(aaccount[activeAccount], KEY_UNLOCK_ATTEMPTS, unlockAttempts + "");
            am.setUserData(aaccount[activeAccount], KEY_LAST_UNLOCK_ATTEMPT, Util.getTimestamp() + "");
        }
    }

    public static void enablePIN(String pin) {
        if (enabled) {
            am.setUserData(aaccount[activeAccount], KEY_PIN, pin);
        }
    }

    public static void disablePIN() {
        if (enabled) {
            am.setUserData(aaccount[activeAccount], KEY_PIN, "");
        }
    }

    public static boolean hasPIN() {
        return (enabled && !am.getUserData(aaccount[activeAccount], KEY_PIN).equals(""));
    }

    public static ArrayList<String> getAllAccounts() {
        ArrayList<String> servers = new ArrayList<>();

        for (Account a : aaccount) {
            servers.add(am.getUserData(a, KEY_SERVER));
        }

        return servers;
    }
}