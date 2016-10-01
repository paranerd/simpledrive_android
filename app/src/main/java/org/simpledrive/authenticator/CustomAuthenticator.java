package org.simpledrive.authenticator;

import android.accounts.Account;
import android.accounts.AccountManager;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import org.simpledrive.helper.Connection;
import org.simpledrive.helper.Util;

import java.util.ArrayList;

public class CustomAuthenticator {
    private static AccountManager am;
    private static Account[] aaccount;
    private static Account active;

    public static final int MAX_UNLOCK_ATTEMPTS = 3;
    public static final int COOLDOWN_ADD = 5000;

    private static String KEY_USER = "user";
    private static String KEY_TOKEN = "token";
    private static String KEY_SERVER = "server";
    private static String KEY_PIN = "pin";
    private static String KEY_UNLOCK_ATTEMPTS = "unlockAttempts";
    private static String KEY_LAST_UNLOCK_ATTEMPT = "lastUnlockAttempt";
    private static String KEY_LOCKED = "locked";
    private static String KEY_ACTIVE = "active";

    public static boolean enable(Context context) {
        am = AccountManager.get(context);
        aaccount = am.getAccountsByType("org.simpledrive");

        for (Account a : aaccount) {
            if (am.getUserData(a, KEY_ACTIVE).equals("1")) {
                active = a;
                return true;
            }
        }

        return setActiveAccount("");
    }

    public static boolean addAccount(String username, String password, String server, String token) {
        String accountName = username + "@" + server;
        Account account = new Account(accountName, "org.simpledrive");
        Bundle userdata = new Bundle();
        userdata.putString(KEY_USER, username);
        userdata.putString(KEY_SERVER, server);
        userdata.putString(KEY_TOKEN, token);
        userdata.putString(KEY_UNLOCK_ATTEMPTS, "0");
        userdata.putString(KEY_LAST_UNLOCK_ATTEMPT, "0");
        userdata.putString(KEY_PIN, "");
        userdata.putString(KEY_ACTIVE, "1");
        userdata.putString(KEY_LOCKED, "0");

        Connection.setToken(token);

        if (am.addAccountExplicitly(account, password, userdata)) {
            active = account;
            setActiveAccount(accountName);
            return true;
        }

        return false;
    }

    public static boolean removeAccount(String username) {
        username = (username.length() > 0) ? username : active.name;

        for (Account a : aaccount) {
            Log.i(a.name, username);
            if (a.name.equals(username)) {
                am.removeAccount(a, null, null, null);
                return setActiveAccount("");
            }
        }

        return false;
    }

    public static boolean setActiveAccount(String accountName) {
        Account[] allAccounts = am.getAccountsByType("org.simpledrive");
        boolean exists = false;

        for (Account a : allAccounts) {
            am.setUserData(a, KEY_ACTIVE, "0");
            if (a.name.equals(accountName)) {
                exists = true;
            }
        }

        if (exists) {
            for (Account a : allAccounts) {
                if (a.name.equals(accountName)) {
                    am.setUserData(a, KEY_ACTIVE, "1");
                    active = a;
                    return true;
                }
            }
        }
        else if (allAccounts.length > 0){
            am.setUserData(allAccounts[0], KEY_ACTIVE, "1");
            active = allAccounts[0];
            return true;
        }

        return false;
    }

    public static String getUsername() {
        return (active != null) ? am.getUserData(active, KEY_USER) : "";
    }

    public static String getAccountname() {
        return (active != null) ? active.name : "";
    }

    public static String getPassword() {
        return (active != null) ? am.getPassword(active) : "";
    }

    public static String getServer() {
        return (active != null) ? am.getUserData(active, KEY_SERVER) : "";
    }

    public static void updateToken(String token) {
        if (active != null) {
            am.setUserData(active, KEY_TOKEN, token);
            Connection.setToken(token);
        }
    }

    public static void lock() {
        if (active != null && hasPIN()) {
            am.setUserData(active, KEY_LOCKED, "1");
        }
    }

    public static boolean isLocked() {
        if (active != null) {
            return am.getUserData(active, KEY_LOCKED).equals("1");
        }
        return false;
    }

    public static boolean unlock(String enteredPin) {
        if (active != null) {
            String pin = am.getUserData(active, KEY_PIN);

            if (getCooldown() > 0) {
                return false;
            }

            if (pin.equals("")|| pin.equals(enteredPin)) {
                resetUnlockCounter();
                am.setUserData(active, KEY_LOCKED, "0");
                return true;
            }
            else {
                incrementUnlockCounter();
            }
        }

        return false;
    }

    public static long getRemainingUnlockAttempts() {
        return (active != null) ? MAX_UNLOCK_ATTEMPTS - Long.parseLong(am.getUserData(active, KEY_UNLOCK_ATTEMPTS)) : 0;
    }

    public static long getCooldown() {
        if (active != null) {
            Long unlockAttempts = Long.parseLong(am.getUserData(active, KEY_UNLOCK_ATTEMPTS));
            Long lastUnlockAttempt = Long.parseLong(am.getUserData(active, KEY_LAST_UNLOCK_ATTEMPT));
            return (lastUnlockAttempt + (unlockAttempts - (MAX_UNLOCK_ATTEMPTS - 1)) * COOLDOWN_ADD) - Util.getTimestamp();
        }
        return 0;
    }

    public static void resetUnlockCounter() {
        if (active != null) {
            am.setUserData(active, KEY_UNLOCK_ATTEMPTS, "0");
        }
    }

    public static void incrementUnlockCounter() {
        if (active != null) {
            Long unlockAttempts = Long.parseLong(am.getUserData(active, KEY_UNLOCK_ATTEMPTS));
            unlockAttempts++;
            am.setUserData(active, KEY_UNLOCK_ATTEMPTS, unlockAttempts + "");
            am.setUserData(active, KEY_LAST_UNLOCK_ATTEMPT, Util.getTimestamp() + "");
        }
    }

    public static void enablePIN(String pin) {
        if (active != null) {
            am.setUserData(active, KEY_PIN, pin);
        }
    }

    public static void disablePIN() {
        if (active != null) {
            am.setUserData(active, KEY_PIN, "");
        }
    }

    public static boolean hasPIN() {
        return (active != null && !am.getUserData(active, KEY_PIN).equals(""));
    }

    public static ArrayList<String> getAllAccounts(boolean includeActive) {
        ArrayList<String> accounts = new ArrayList<>();
        Account[] allAccounts = am.getAccountsByType("org.simpledrive");

        for (Account a : allAccounts) {
            if (includeActive || !a.name.equals(active.name)) {
                accounts.add(a.name);
            }
        }

        return accounts;
    }

    public static boolean isActive(Context context, String username) {
        am = AccountManager.get(context);
        Account[] allAccounts = am.getAccountsByType("org.simpledrive");

        for (Account a : allAccounts) {
            Log.i(am.getUserData(a, KEY_USER), username);
            if (am.getUserData(a, KEY_USER).equals(username) && am.getUserData(a, KEY_ACTIVE).equals("1")) {
                return true;
            }
        }

        return false;
    }
}