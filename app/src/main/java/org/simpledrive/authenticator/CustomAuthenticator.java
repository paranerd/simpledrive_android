package org.simpledrive.authenticator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.os.Bundle;

import org.simpledrive.helper.DownloadManager;
import org.simpledrive.helper.UploadManager;
import org.simpledrive.helper.Util;
import org.simpledrive.models.AccountItem;

import java.util.ArrayList;

public class CustomAuthenticator {
    // General
    private static Context ctx;
    // Accounts
    private static AccountManager am;
    private static Account[] aaccount;
    public static boolean activeAccountChanged = false;

    // Constants
    public static final int MAX_UNLOCK_ATTEMPTS = 3;
    private static final int COOLDOWN_ADD = 5000;
    private static final String ACCOUNT_TYPE = "org.simpledrive";
    private static final String KEY_USER = "user";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_SERVER = "server";
    private static final String KEY_PIN = "pin";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_UNLOCK_ATTEMPTS = "unlockAttempts";
    private static final String KEY_LAST_UNLOCK_ATTEMPT = "lastUnlockAttempt";
    private static final String KEY_LOCKED = "locked";
    private static final String KEY_ACTIVE = "active";
    private static final String TRUE = "1";
    private static final String FALSE = "0";

    private static void refresh() {
        am = AccountManager.get(ctx);
        aaccount = am.getAccountsByType(ACCOUNT_TYPE);
    }

    public static boolean enable(Context context) {
        ctx = context;
        refresh();

        for (Account a : aaccount) {
            if (am.getUserData(a, KEY_ACTIVE).equals(TRUE)) {
                activeAccountChanged = false;
                return true;
            }
        }

        activeAccountChanged = true;
        return aaccount.length > 0;
    }

    public static boolean accountExists(String username, String server) {
        refresh();
        for (Account a : aaccount) {
            if (a.name.equals(username + "@" + server)) {
                return true;
            }
        }

        return false;
    }

    public static boolean addAccount(String username, String password, String server, String token) {
        final String accountName = username + "@" + server;
        final Account account = new Account(accountName, ACCOUNT_TYPE);
        Bundle userdata = new Bundle();
        userdata.putString(KEY_USER, username);
        userdata.putString(KEY_SERVER, server);
        userdata.putString(KEY_TOKEN, token);
        userdata.putString(KEY_UNLOCK_ATTEMPTS, "0");
        userdata.putString(KEY_LAST_UNLOCK_ATTEMPT, "0");
        userdata.putString(KEY_NICKNAME, "");
        userdata.putString(KEY_PIN, "");
        userdata.putString(KEY_ACTIVE, FALSE);
        userdata.putString(KEY_LOCKED, FALSE);

        if (am.addAccountExplicitly(account, password, userdata)) {
            setActive(accountName);
            return true;
        }

        return false;
    }

    private static Account getActiveAccount() {
        refresh();

        for (Account a : aaccount) {
            if (am.getUserData(a, KEY_ACTIVE).equals(TRUE)) {
                return a;
            }
        }

        if (aaccount.length > 0) {
            am.setUserData(aaccount[0], KEY_ACTIVE, TRUE);
            return aaccount[0];
        }

        return null;
    }

    public static boolean removeAccount(String accountName) {
        accountName = (accountName.length() == 0 && getActiveAccount() != null) ? getActiveAccount().name : accountName;

        for (Account a : aaccount) {
            if (a.name.equals(accountName)) {
                am.removeAccount(a, null, null, null);
                return true;
            }
        }

        return false;
    }

    public static boolean setNickname(String accountName, String nickname) {
        if (accountName.length() == 0) {
            return false;
        }

        for (Account a : aaccount) {
            if (a.name.equals(accountName)) {
                am.setUserData(a, KEY_NICKNAME, nickname);
                return true;
            }
        }

        return false;
    }

    public static void logout() {
        if (getActiveAccount() != null) {
            removeAccount(getActiveAccount().name);
        }
    }

    public static void setActive(String accountName) {
        if (DownloadManager.isRunning() || UploadManager.isRunning()) {
            return;
        }

        refresh();
        lock();

        for (Account a : aaccount) {
            am.setUserData(a, KEY_ACTIVE, FALSE);
            if (a.name.equals(accountName)) {
                am.setUserData(a, KEY_ACTIVE, TRUE);
            }
        }
    }

    public static String getUsername() {
        return (getActiveAccount() != null) ? am.getUserData(getActiveAccount(), KEY_USER) : "";
    }

    public static String getPassword() {
        return (getActiveAccount() != null) ? am.getPassword(getActiveAccount()) : "";
    }

    public static String getServer() {
        return (getActiveAccount() != null) ? am.getUserData(getActiveAccount(), KEY_SERVER) : "";
    }

    public static String getToken() {
        return (getActiveAccount() != null) ? am.getUserData(getActiveAccount(), KEY_TOKEN) : "";
    }

    public static void setToken(String token) {
        if (getActiveAccount() != null) {
            am.setUserData(getActiveAccount(), KEY_TOKEN, token);
        }
    }

    public static void lock() {
        if (hasPIN()) {
            am.setUserData(getActiveAccount(), KEY_LOCKED, TRUE);
        }
    }

    public static boolean isLocked() {
        return getActiveAccount() != null && am.getUserData(getActiveAccount(), KEY_LOCKED).equals(TRUE);
    }

    public static boolean unlock(String enteredPin) {
        if (getActiveAccount() != null) {
            String pin = am.getUserData(getActiveAccount(), KEY_PIN);

            if (getCooldown() > 0) {
                return false;
            }

            if (pin.equals(enteredPin)) {
                am.setUserData(getActiveAccount(), KEY_UNLOCK_ATTEMPTS, "0");
                am.setUserData(getActiveAccount(), KEY_LOCKED, FALSE);
                return true;
            }
            else {
                incrementUnlockCounter();
            }
        }

        return false;
    }

    public static long getRemainingUnlockAttempts() {
        return (getActiveAccount() != null) ? MAX_UNLOCK_ATTEMPTS - Long.parseLong(am.getUserData(getActiveAccount(), KEY_UNLOCK_ATTEMPTS)) : 0;
    }

    public static long getCooldown() {
        if (getActiveAccount() != null) {
            Long unlockAttempts = Long.parseLong(am.getUserData(getActiveAccount(), KEY_UNLOCK_ATTEMPTS));
            Long lastUnlockAttempt = Long.parseLong(am.getUserData(getActiveAccount(), KEY_LAST_UNLOCK_ATTEMPT));
            return (long) Math.ceil(((lastUnlockAttempt + (unlockAttempts - (MAX_UNLOCK_ATTEMPTS - 1)) * COOLDOWN_ADD) - Util.getTimestamp()) / 1000.0);
        }
        return 0;
    }

    private static void incrementUnlockCounter() {
        if (getActiveAccount() != null) {
            Long unlockAttempts = Long.parseLong(am.getUserData(getActiveAccount(), KEY_UNLOCK_ATTEMPTS));
            unlockAttempts++;
            am.setUserData(getActiveAccount(), KEY_UNLOCK_ATTEMPTS, unlockAttempts + "");
            am.setUserData(getActiveAccount(), KEY_LAST_UNLOCK_ATTEMPT, Util.getTimestamp() + "");
        }
    }

    public static void setPIN(String pin) {
        if (getActiveAccount() != null) {
            am.setUserData(getActiveAccount(), KEY_PIN, pin);
        }
    }

    public static boolean hasPIN() {
        return (getActiveAccount() != null && !am.getUserData(getActiveAccount(), KEY_PIN).equals(""));
    }

    public static ArrayList<AccountItem> getAllAccounts(boolean includeActive) {
        refresh();
        ArrayList<AccountItem> accounts = new ArrayList<>();
        String active = (getActiveAccount() != null) ? getActiveAccount().name : "";

        for (Account a : aaccount) {
            if (includeActive || !a.name.equals(active)) {
                String name = a.name;
                String server = am.getUserData(a, KEY_SERVER);
                String user = am.getUserData(a, KEY_USER);
                String nick = am.getUserData(a, KEY_NICKNAME);

                accounts.add(new AccountItem(name, server, user, nick));
            }
        }

        return accounts;
    }

    public static boolean isActive(String username) {
        refresh();

        for (Account a : aaccount) {
            if (am.getUserData(a, KEY_USER).equals(username) && am.getUserData(a, KEY_ACTIVE).equals(TRUE)) {
                return true;
            }
        }

        return false;
    }
}