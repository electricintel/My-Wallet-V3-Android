package info.blockchain.wallet.view;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

import info.blockchain.wallet.access.AccessState;
import info.blockchain.wallet.connectivity.ConnectivityStatus;
import info.blockchain.wallet.payload.Payload;
import info.blockchain.wallet.payload.PayloadManager;
import info.blockchain.wallet.util.AppUtil;
import info.blockchain.wallet.util.CharSequenceX;
import info.blockchain.wallet.util.PrefsUtil;
import info.blockchain.wallet.util.ViewUtils;
import info.blockchain.wallet.view.helpers.ToastCustom;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import piuk.blockchain.android.BaseAuthActivity;
import piuk.blockchain.android.R;
import piuk.blockchain.android.databinding.ActivityPinEntryBinding;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.exceptions.Exceptions;
import rx.schedulers.Schedulers;

public class PinEntryActivity extends BaseAuthActivity {

    private static final int COOL_DOWN_MILLIS = 2 * 1000;
    private static final int PIN_LENGTH = 4;
    private static final int maxAttempts = 4;
    String userEnteredPIN = "";
    String userEnteredPINConfirm = null;

    TextView[] pinBoxArray = null;
    boolean allowExit = true;
    private long mBackPressed;
    private ProgressDialog progress = null;
    private String strEmail = null;
    private String strPassword = null;
    private PrefsUtil prefs;
    private AppUtil appUtil;
    private PayloadManager payloadManager;

    private ActivityPinEntryBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_pin_entry);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        prefs = new PrefsUtil(this);
        appUtil = new AppUtil(this);
        payloadManager = PayloadManager.getInstance();

        // Coming from CreateWalletFragment
        getBundleData();
        if (strPassword != null && strEmail != null) {
            allowExit = false;
            saveLoginAndPassword();
            createWallet();
        }

        // Set title state
        if (isCreatingNewPin()) {
            binding.titleBox.setText(R.string.create_pin);
        } else {
            binding.titleBox.setText(R.string.pin_entry);
        }

        pinBoxArray = new TextView[PIN_LENGTH];
        pinBoxArray[0] = binding.pinBox0;
        pinBoxArray[1] = binding.pinBox1;
        pinBoxArray[2] = binding.pinBox2;
        pinBoxArray[3] = binding.pinBox3;

        if (!ConnectivityStatus.hasConnectivity(this)) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogStyle);
            builder.setMessage(getString(R.string.check_connectivity_exit))
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_continue,
                            (dialog, id) -> {
                                restartPage();
                            });

            builder.create().show();
        }

        int fails = prefs.getValue(PrefsUtil.KEY_PIN_FAILS, 0);
        if (fails >= maxAttempts) {
            ToastCustom.makeText(getApplicationContext(), getString(R.string.pin_4_strikes), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);

            payloadManager.getPayload().stepNumber = 0;

            new AlertDialog.Builder(getActivity(), R.style.AlertDialogStyle)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.password_or_wipe)
                    .setCancelable(false)
                    .setPositiveButton(R.string.use_password, (dialog, whichButton) -> showValidationDialog())
                    .setNegativeButton(R.string.wipe_wallet, (dialog, whichButton) -> appUtil.clearCredentialsAndRestart())
                    .show();
        }
    }

    private boolean isCreatingNewPin() {
        return prefs.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, "").length() < 1;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Test for screen overlays before user enters PIN
        // consume event
        return appUtil.detectObscuredWindow(event) || super.dispatchTouchEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        userEnteredPIN = "";
        clearPinBoxes();
    }

    @Override
    protected void startLogoutTimer() {
        // No-op
    }

    @Override
    public void onBackPressed() {
        if (allowExit) {
            if (mBackPressed + COOL_DOWN_MILLIS > System.currentTimeMillis()) {
                AccessState.getInstance().logout(this);
                return;
            } else {
                ToastCustom.makeText(this, getString(R.string.exit_confirm), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_GENERAL);
            }

            mBackPressed = System.currentTimeMillis();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    private void saveLoginAndPassword() {
        prefs.setValue(PrefsUtil.KEY_EMAIL, strEmail);
        payloadManager.setEmail(strEmail);
        payloadManager.setTempPassword(new CharSequenceX(strPassword));
    }

    private void createWallet() {
        showProgressDialog(getText(R.string.create_wallet) + "...");

        createWalletObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doAfterTerminate(this::dismissProgressDialog)
                .subscribe(payload -> {
                    appUtil.setNewlyCreated(true);
                    if (payload != null) {
                        //Successfully created and saved
                        prefs.setValue(PrefsUtil.KEY_GUID, payload.getGuid());
                        appUtil.setSharedKey(payload.getSharedKey());
                    } else {
                        ToastCustom.makeText(getApplicationContext(), getApplicationContext().getString(R.string.remote_save_ko),
                                ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                    }
                }, throwable -> {
                    ToastCustom.makeText(getApplicationContext(), getString(R.string.hd_error), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                    appUtil.clearCredentialsAndRestart();
                });
    }

    private void getBundleData() {
        Bundle extras = getIntent().getExtras();

        if (extras != null && extras.containsKey("_email")) {
            strEmail = extras.getString("_email");
        }

        if (extras != null && extras.containsKey("_pw")) {
            strPassword = extras.getString("_pw");
        }
    }

    private void updatePayload(final CharSequenceX password) {
        showProgressDialog(getString(R.string.decrypting_wallet));

        createUpdatePayloadObservable(password)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnTerminate(this::dismissProgressDialog)
                .subscribe(aVoid -> {

                    payloadManager.setTempPassword(password);
                    appUtil.setSharedKey(payloadManager.getPayload().getSharedKey());

                    double walletVersion = payloadManager.getVersion();

                    if (walletVersion > PayloadManager.SUPPORTED_ENCRYPTION_VERSION) {
                        new AlertDialog.Builder(getActivity(), R.style.AlertDialogStyle)
                                .setTitle(R.string.warning)
                                .setMessage(String.format(getString(R.string.unsupported_encryption_version), (int) walletVersion))
                                .setCancelable(false)
                                .setPositiveButton(R.string.exit, (dialog, whichButton) -> AccessState.getInstance().logout(getActivity()))
                                .setNegativeButton(R.string.logout, (dialog, which) -> {
                                    appUtil.clearCredentialsAndRestart();
                                    appUtil.restartApp();
                                })
                                .show();
                    } else {
                        setAccountLabelIfNecessary();

                        try {
                            int previousVersionCode = prefs.getValue(PrefsUtil.KEY_CURRENT_APP_VERSION, 0);
                            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);

                            //If upgrade detected - reset last reminder so we can warn user again + set new app version in prefs
                            if (previousVersionCode != packageInfo.versionCode) {
                                prefs.setValue(PrefsUtil.KEY_HD_UPGRADE_LAST_REMINDER, 0L);
                                prefs.setValue(PrefsUtil.KEY_CURRENT_APP_VERSION, packageInfo.versionCode);
                            }

                        } catch (PackageManager.NameNotFoundException e) {
                            Log.e(PinEntryActivity.class.getSimpleName(), "updatePayload: ", e);
                        }

                        if (prefs.getValue(PrefsUtil.KEY_HD_UPGRADE_LAST_REMINDER, 0L) == 0L && !payloadManager.getPayload().isUpgraded()) {
                            Intent intent = new Intent(getActivity(), UpgradeWalletActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        } else {
                            appUtil.restartAppWithVerifiedPin();
                        }
                    }

                }, throwable -> {
                    appUtil.clearCredentialsAndRestart();
                });
    }

    private void setAccountLabelIfNecessary() {
        if (appUtil.isNewlyCreated() && payloadManager.getPayload().getHdWallet() != null
                && (payloadManager.getPayload().getHdWallet().getAccounts().get(0).getLabel() == null
                || payloadManager.getPayload().getHdWallet().getAccounts().get(0).getLabel().isEmpty())) {
            payloadManager.getPayload().getHdWallet().getAccounts().get(0).setLabel(getResources().getString(R.string.default_wallet_name));
        }
    }

    private Context getActivity() {
        return this;
    }

    private void createNewPin(String pin) {
        showProgressDialog(getString(R.string.creating_pin));

        createNewPinObservable(pin)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(createSuccessful -> {
                    dismissProgressDialog();
                    if (createSuccessful) {
                        prefs.setValue(PrefsUtil.KEY_PIN_FAILS, 0);
                        updatePayload(payloadManager.getTempPassword());
                    } else {
                        throw Exceptions.propagate(new Throwable("Pin create failed"));
                    }
                }, throwable -> {
                    dismissProgressDialog();
                    ToastCustom.makeText(getActivity(), getString(R.string.create_pin_failed), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                    prefs.clear();
                    appUtil.restartApp();
                });
    }

    private void validatePIN(final String pin) {
        showProgressDialog(getString(R.string.validating_pin));

        createValidatePinObservable(pin)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(password -> {
                    dismissProgressDialog();
                    if (password != null) {
                        prefs.setValue(PrefsUtil.KEY_PIN_FAILS, 0);
                        updatePayload(password);
                    } else {
                        incrementFailureCount();
                    }
                }, throwable -> {
                    dismissProgressDialog();
                    ToastCustom.makeText(getActivity(), getString(R.string.unexpected_error), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                    restartPage();
                });
    }

    private void validatePassword(final CharSequenceX password) {
        showProgressDialog(getString(R.string.validating_password));

        createUpdatePayloadObservable(password)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(() -> payloadManager.setTempPassword(new CharSequenceX("")))
                .doAfterTerminate(this::dismissProgressDialog)
                .subscribe(o -> {
                    ToastCustom.makeText(getActivity(), getString(R.string.pin_4_strikes_password_accepted), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_OK);

                    payloadManager.setTempPassword(password);
                    prefs.removeValue(PrefsUtil.KEY_PIN_FAILS);
                    prefs.removeValue(PrefsUtil.KEY_PIN_IDENTIFIER);

                    restartPage();
                }, throwable -> {
                    ToastCustom.makeText(getActivity(), getString(R.string.invalid_password), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                    dismissProgressDialog();
                    showValidationDialog();
                });
    }

    public void padClicked(View view) {
        if (userEnteredPIN.length() == PIN_LENGTH) {
            return;
        }

        // Append tapped #
        userEnteredPIN = userEnteredPIN + view.getTag().toString().substring(0, 1);
        pinBoxArray[userEnteredPIN.length() - 1].setBackgroundResource(R.drawable.rounded_view_dark_blue);

        // Perform appropriate action if PIN_LENGTH has been reached
        if (userEnteredPIN.length() == PIN_LENGTH) {

            // Throw error on '0000' to avoid server-side type issue
            if (userEnteredPIN.equals("0000")) {
                ToastCustom.makeText(getActivity(), getString(R.string.zero_pin), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                new Handler().postDelayed(this::clearPinViewAndReset, 200);
                return;
            }

            // Only show warning on first entry and if user is creating a new PIN
            if (isCreatingNewPin() && isPinCommon(userEnteredPIN) && userEnteredPINConfirm == null) {
                showCommonPinWarning(new PinWarningCallback() {
                    @Override
                    public void tryAgainClicked() {
                        clearPinViewAndReset();
                    }

                    @Override
                    public void continueClicked() {
                        validateAndConfirmPin();
                    }
                });
            } else {
                validateAndConfirmPin();
            }
        }
    }

    private void clearPinViewAndReset() {
        clearPinBoxes();
        userEnteredPIN = "";
        userEnteredPINConfirm = null;
    }

    private void validateAndConfirmPin() {
        // Validate
        if (prefs.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, "").length() >= 1) {
            binding.titleBox.setVisibility(View.INVISIBLE);
            validatePIN(userEnteredPIN);
        } else if (userEnteredPINConfirm == null) {
            // End of Create -  Change to Confirm
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(() -> {
                        binding.titleBox.setText(R.string.confirm_pin);
                        clearPinBoxes();
                        userEnteredPINConfirm = userEnteredPIN;
                        userEnteredPIN = "";
                    });
                }
            }, 200);

        } else if (userEnteredPINConfirm.equals(userEnteredPIN)) {
            // End of Confirm - Pin is confirmed
            createNewPin(userEnteredPIN); // Pin is confirmed. Save to server.

        } else {
            // End of Confirm - Pin Mismatch
            ToastCustom.makeText(getActivity(), getString(R.string.pin_mismatch_error), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
            new Handler().postDelayed(() -> {
                clearPinViewAndReset();
                binding.titleBox.setText(R.string.create_pin);
            }, 200);
        }
    }

    public void deleteClicked(View view) {
        if (userEnteredPIN.length() > 0) {
            //Remove last char from pin string
            userEnteredPIN = userEnteredPIN.substring(0, userEnteredPIN.length() - 1);

            //Clear last box
            pinBoxArray[userEnteredPIN.length()].setBackgroundResource(R.drawable.rounded_view_blue_white_border);
        }
    }

    private void clearPinBoxes() {
        if (userEnteredPIN.length() > 0) {
            for (TextView pinBox : pinBoxArray) {
                // Reset PIN buttons to blank
                pinBox.setBackgroundResource(R.drawable.rounded_view_blue_white_border);
            }
        }
    }

    private void incrementFailureCount() {
        int fails = prefs.getValue(PrefsUtil.KEY_PIN_FAILS, 0);
        prefs.setValue(PrefsUtil.KEY_PIN_FAILS, ++fails);

        ToastCustom.makeText(getActivity(), getString(R.string.invalid_pin), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);

        restartPage();
    }

    private void restartPage() {
        Intent intent = new Intent(getActivity(), PinEntryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private boolean isPinCommon(String pin) {
        List<String> commonPins = new ArrayList<String>() {{
            add("1234");
            add("1111");
            add("1212");
            add("7777");
            add("1004");
        }};
        return commonPins.contains(pin);
    }

    private void showCommonPinWarning(PinWarningCallback callback) {
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setTitle(R.string.common_pin_dialog_title)
                .setMessage(R.string.common_pin_dialog_message)
                .setPositiveButton(R.string.common_pin_dialog_try_again, (dialogInterface, i) -> callback.tryAgainClicked())
                .setNegativeButton(R.string.common_pin_dialog_continue, (dialogInterface, i) -> callback.continueClicked())
                .setCancelable(false)
                .create();

        dialog.show();
    }

    private void showValidationDialog() {
        final AppCompatEditText password = new AppCompatEditText(this);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        FrameLayout frameLayout = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int marginInPixels = (int) ViewUtils.convertDpToPixel(20, this);
        params.setMargins(marginInPixels, 0, marginInPixels, 0);
        frameLayout.addView(password, params);

        new AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setTitle(R.string.app_name)
                .setMessage(getActivity().getString(R.string.password_entry))
                .setView(frameLayout)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (dialog, whichButton) -> appUtil.restartApp())
                .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
                    final String pw = password.getText().toString();

                    if (pw.length() > 0) {
                        validatePassword(new CharSequenceX(pw));
                    } else {
                        incrementFailureCount();
                    }

                }).show();
    }

    private interface PinWarningCallback {
        void tryAgainClicked();

        void continueClicked();
    }

    private void showProgressDialog(String string) {
        dismissProgressDialog();
        progress = new ProgressDialog(getActivity());
        progress.setCancelable(false);
        progress.setTitle(R.string.app_name);
        progress.setMessage(string);
        if (!isFinishing()) progress.show();
    }

    private void dismissProgressDialog() {
        if (progress != null) {
            progress.dismiss();
            progress = null;
        }
    }

    /**
     * Observables
     * TODO: These can be moved into a data manager of some description in the future
     */

    private Observable<Payload> createWalletObservable() {
        return Observable.fromCallable(() -> payloadManager.createHDWallet(strPassword, getString(R.string.default_wallet_name)));
    }

    private Observable<CharSequenceX> createValidatePinObservable(String pin) {
        return Observable.fromCallable(() -> AccessState.getInstance().validatePIN(pin));
    }

    private Observable<Boolean> createNewPinObservable(String pin) {
        return Observable.fromCallable(() -> AccessState.getInstance().createPIN(payloadManager.getTempPassword(), pin));
    }

    private Observable<Void> createUpdatePayloadObservable(CharSequenceX password) {
        return Observable.defer(() -> Observable.create(subscriber -> {
            try {
                payloadManager.initiatePayload(
                        prefs.getValue(PrefsUtil.KEY_SHARED_KEY, ""),
                        prefs.getValue(PrefsUtil.KEY_GUID, ""),
                        password,
                        new PayloadManager.InitiatePayloadListener() {
                            @Override
                            public void onInitSuccess() {
                                subscriber.onNext(null);
                                subscriber.onCompleted();
                            }

                            @Override
                            public void onInitPairFail() {
                                subscriber.onError(new Throwable("onInitPairFail"));
                            }

                            @Override
                            public void onInitCreateFail(String s) {
                                subscriber.onError(new Throwable("onInitCreateFail: " + s));
                            }
                        });
            } catch (Exception e) {
                subscriber.onError(new Throwable("Create password failed: " + e));
            }
        }));
    }
}