package piuk.blockchain.android;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.security.ProviderInstaller;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.multidex.MultiDex;
import android.util.Log;

import info.blockchain.wallet.access.AccessState;
import info.blockchain.wallet.util.SSLVerifyUtil;

import piuk.blockchain.android.di.Injector;
import piuk.blockchain.android.exceptions.LoggingExceptionHandler;
import rx.plugins.RxJavaHooks;

/**
 * Created by adambennett on 04/08/2016.
 */

public class BlockchainApplication extends Application {

    private static final String TAG = BlockchainApplication.class.getSimpleName();
    private static final String RX_ERROR_TAG = "RxJava Error";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            MultiDex.install(base);
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Injector.getInstance().init(this);

        if (!BuildConfig.DEBUG) {
            new LoggingExceptionHandler();
        }

        RxJavaHooks.enableAssemblyTracking();
        RxJavaHooks.setOnError(throwable -> Log.d(RX_ERROR_TAG, throwable.getMessage(), throwable));

        AccessState.getInstance().initAccessState(this);

        checkSecurityProviderAndPatchIfNeeded();
        verifySslPinning();
    }

    /**
     * This patches a device's Security Provider asynchronously to help defend against various
     * vulnerabilities. This provider is normally updated in Google Play Services anyway, but this
     * will catch any immediate issues that haven't been fixed in a slow rollout.
     *
     * In the future, we may want to show some kind of warning to users or even
     * stop the app, but this will harm users with versions of Android without GMS approval.
     *
     * @see <a href="https://developer.android.com/training/articles/security-gms-provider.html">Updating Your Security Provider</a>
     */
    private void checkSecurityProviderAndPatchIfNeeded() {
        ProviderInstaller.installIfNeededAsync(this, new ProviderInstaller.ProviderInstallListener() {
            @Override
            public void onProviderInstalled() {
                Log.i(TAG, "Security Provider installed");
            }

            @Override
            public void onProviderInstallFailed(int errorCode, Intent intent) {
                if (GoogleApiAvailability.getInstance().isUserResolvableError(errorCode)) {
                    showError(errorCode);
                } else {
                    // Google Play services is not available.
                    onProviderInstallerNotAvailable();
                }
            }
        });
    }

    /**
     * Show a dialog prompting the user to
     * install/update/enable Google Play services.
     *
     * @param errorCode Recoverable error code
     */
    private void showError(int errorCode) {
        // TODO: 05/08/2016 Decide if we should alert users here or not
        Log.e(TAG, "Security Provider install failed with recoverable error: " +
                GoogleApiAvailability.getInstance().getErrorString(errorCode));
    }

    /**
     * This is reached if the provider cannot be updated for some reason.
     * App should consider all HTTP communication to be vulnerable, and take
     * appropriate action.
     */
    private void onProviderInstallerNotAvailable() {
        // TODO: 05/08/2016 Decide if we should take action here or not
        Log.wtf(TAG, "Security Provider Installer not available");
    }

    private void verifySslPinning() {
        new SSLVerifyUtil(this).validateSSL();
    }
}
