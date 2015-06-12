package simpledrive.library;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

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
