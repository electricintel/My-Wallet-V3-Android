package info.blockchain.wallet.view.helpers;

import android.support.v7.app.AlertDialog;
import android.content.Context;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import info.blockchain.wallet.payload.PayloadManager;
import info.blockchain.wallet.util.CharSequenceX;
import info.blockchain.wallet.util.DoubleEncryptionFactory;
import info.blockchain.wallet.util.ViewUtils;

import org.apache.commons.lang3.StringUtils;

import piuk.blockchain.android.R;

public class SecondPasswordHandler {

    private Context context;
    private PayloadManager payloadManager;

    public SecondPasswordHandler(Context context) {
        this.context = context;
        this.payloadManager = PayloadManager.getInstance();
    }

    public interface ResultListener {
        void onNoSecondPassword();

        void onSecondPasswordValidated(String validateSecondPassword);
    }

    public void validate(final ResultListener listener) {

        if (!payloadManager.getPayload().isDoubleEncrypted()) {
            listener.onNoSecondPassword();
        } else {

            final AppCompatEditText passwordField = new AppCompatEditText(context);
            passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

            FrameLayout frameLayout = new FrameLayout(context);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int marginInPixels = (int) ViewUtils.convertDpToPixel(20, context);
            params.setMargins(marginInPixels, 0, marginInPixels, 0);
            frameLayout.addView(passwordField, params);

            new AlertDialog.Builder(context, R.style.AlertDialogStyle)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.enter_double_encryption_pw)
                    .setView(frameLayout)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {

                        String secondPassword = passwordField.getText().toString();

                        if (secondPassword.length() > 0 && DoubleEncryptionFactory.getInstance().validateSecondPassword(
                                payloadManager.getPayload().getDoublePasswordHash(),
                                payloadManager.getPayload().getSharedKey(),
                                new CharSequenceX(secondPassword),
                                payloadManager.getPayload().getOptions().getIterations()) && !StringUtils.isEmpty(secondPassword)) {

                            listener.onSecondPasswordValidated(secondPassword);

                        } else {
                            ToastCustom.makeText(context, context.getString(R.string.double_encryption_password_error), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                        }
                    }).setNegativeButton(android.R.string.cancel, null).show();
        }
    }
}
