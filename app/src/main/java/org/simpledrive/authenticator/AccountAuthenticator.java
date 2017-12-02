package org.simpledrive.authenticator;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.simpledrive.activities.Login;

public class AccountAuthenticator extends AbstractAccountAuthenticator {
  private final Context mContext;

  AccountAuthenticator(Context paramContext) {
    super(paramContext);
    mContext = paramContext;
  }

  public Bundle addAccount(AccountAuthenticatorResponse response, String paramString1, String paramString2, String[] paramArrayOfString, Bundle paramBundle) {
    final Intent intent = new Intent(mContext, Login.class);
    intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
    final Bundle bundle = new Bundle();
    bundle.putParcelable(AccountManager.KEY_INTENT, intent);
    return bundle;
  }

  public Bundle confirmCredentials(AccountAuthenticatorResponse paramAccountAuthenticatorResponse, Account paramAccount, Bundle paramBundle) {
    return null;
  }

  public Bundle editProperties(AccountAuthenticatorResponse paramAccountAuthenticatorResponse, String paramString) {
    return null;
  }

  public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) {
    return null;
  }

  public String getAuthTokenLabel(String paramString)
  {
    return null;
  }

  public Bundle hasFeatures(AccountAuthenticatorResponse paramAccountAuthenticatorResponse, Account paramAccount, String[] paramArrayOfString) {
    return null;
  }

  public Bundle updateCredentials(AccountAuthenticatorResponse paramAccountAuthenticatorResponse, Account paramAccount, String paramString, Bundle paramBundle) {
    return null;
  }
}