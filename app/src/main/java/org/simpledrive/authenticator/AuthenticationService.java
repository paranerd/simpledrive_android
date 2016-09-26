package org.simpledrive.authenticator;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.simpledrive.authenticator.AccountAuthenticator;

// Referenced classes of package simpledrive.library:
//            a

public class AuthenticationService extends Service
{
	@Override
    public IBinder onBind(Intent intent)
    {
        return new AccountAuthenticator(this).getIBinder();
    }
}
