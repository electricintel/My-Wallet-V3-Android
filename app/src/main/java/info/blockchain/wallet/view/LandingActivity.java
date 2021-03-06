package info.blockchain.wallet.view;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.v7.app.AlertDialog;
import android.view.MotionEvent;

import info.blockchain.wallet.connectivity.ConnectivityStatus;
import info.blockchain.wallet.util.AppUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import piuk.blockchain.android.BaseAuthActivity;
import piuk.blockchain.android.R;
import piuk.blockchain.android.databinding.ActivityLandingBinding;

public class LandingActivity extends BaseAuthActivity {

    private ActivityLandingBinding binding;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({CREATE_FRAGMENT, LOGIN_FRAGMENT})
    public @interface StartingFragment {
    }

    public static final int CREATE_FRAGMENT = 0;
    public static final int LOGIN_FRAGMENT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_landing);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setTitle(R.string.app_name);

        binding.create.setOnClickListener((v) -> startLandingActivity(CREATE_FRAGMENT));
        binding.login.setOnClickListener((v) -> startLandingActivity(LOGIN_FRAGMENT));

        if (!ConnectivityStatus.hasConnectivity(this)) {
            new AlertDialog.Builder(this, R.style.AlertDialogStyle)
                    .setMessage(getString(R.string.check_connectivity_exit))
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_continue, (d, id) -> {
                        Intent intent = new Intent(LandingActivity.this, LandingActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    })
                    .create()
                    .show();
        }
    }

    private void startLandingActivity(@StartingFragment int createFragment) {
        Intent intent = new Intent(LandingActivity.this, PairOrCreateWalletActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("starting_fragment", createFragment);
        startActivity(intent);
    }

    @Override
    public void startLogoutTimer() {
        // No-op
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Test for screen overlays before user creates a new wallet or enters confidential information
        // consume event
        return new AppUtil(this).detectObscuredWindow(event) || super.dispatchTouchEvent(event);
    }
}
