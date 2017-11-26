package com.cks.hiroyuki2.worksupport3;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.Log;
import android.widget.Toast;

import com.cks.hiroyuki2.worksupport3.Activities.MainActivity;
import com.cks.hiroyuki2.worksupport3.Fragments.GroupSettingFragment;
import com.cks.hiroyuki2.worksupprotlib.Entity.Group;
import com.cks.hiroyuki2.worksupprotlib.FbCheckAndWriter;
import com.cks.hiroyuki2.worksupprotlib.FirebaseStorageUtil;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

import org.androidannotations.annotations.EIntentService;
import org.androidannotations.annotations.ServiceAction;

import java.lang.ref.WeakReference;

import static com.cks.hiroyuki2.worksupport3.RxBus.UPDATE_GROUP_PHOTO;
import static com.cks.hiroyuki2.worksupprotlib.FbCheckAndWriter.CODE_SET_VALUE;
import static com.cks.hiroyuki2.worksupprotlib.FbCheckAndWriter.CODE_UPDATE_CHILDREN;
import static com.cks.hiroyuki2.worksupprotlib.FirebaseConnection.getRef;
import static com.cks.hiroyuki2.worksupprotlib.FirebaseStorageUtil.uploadFile;
import static com.cks.hiroyuki2.worksupprotlib.Util.getExtension;
import static com.cks.hiroyuki2.worksupprotlib.Util.logStackTrace;
import static com.cks.hiroyuki2.worksupprotlib.Util.makeScheme;
import static com.cks.hiroyuki2.worksupprotlib.Util.showCompleteNtf;
import static com.cks.hiroyuki2.worksupprotlib.Util.showUploadingNtf;
import static com.cks.hiroyuki2.worksupprotlib.Util.toastNullable;

/**
 * Fbにぶん投げる系
 */

@EIntentService
public class FbIntentService extends IntentService implements OnFailureListener{
    private static final String TAG = "MANUAL_TAG: " + FbIntentService.class.getSimpleName();
    private Handler toastHandler = new Handler(getMainLooper());

    public FbIntentService(){
        // ActivityのstartService(intent);で呼び出されるコンストラクタはこちら
        super("FbIntentService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        //do nothing here
    }

    @ServiceAction
    public void updateGroupName(@NonNull String groupKey, @NonNull String newGroupName){
        Log.d(TAG, "sampleAction() called");
        DatabaseReference ref = getRef("group", groupKey, "groupName");
        FbCheckAndWriter writer = new FbCheckAndWriter(ref, ref, getApplicationContext(), newGroupName) {
            @Override
            public void onSuccess(DatabaseReference ref) {
                Log.d(TAG, "onSuccess: 成功したよね");
                RxBus.publish(RxBus.UPDATE_GROUP_NAME, "てってれー");
            }
        };
        writer.update(CODE_SET_VALUE);
    }

    @ServiceAction
    public void updateGroupPhotoUrl(@NonNull Group group, /*このuriは、ローカルファイルのuri*/ @NonNull Uri uri){
        FirebaseStorageUtil storageUtil = new FirebaseStorageUtil(getApplicationContext(), group);
        toastHandler.post(new DisplayToast(R.string.msg_start_upload));
        String type = getExtension(getApplicationContext(), uri);
        String key = getRef("keyPusher").push().getKey();
        final String fileName = key + "." + type;

        final int ntfId = (int) System.currentTimeMillis();

        uploadFile("group_icon/" + fileName, uri, this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Uri uri = taskSnapshot.getDownloadUrl();
                RxBus.publish(UPDATE_GROUP_PHOTO, uri);/*Firebaseの仕様上NPEはあり得ないので、you can ignore this warning*/

                DatabaseReference ref = getRef("group", group.groupKey, "photoUrl");
                FbCheckAndWriter writer = new FbCheckAndWriter(ref, ref, getApplicationContext(), uri.toString()) {
                    @Override
                    public void onSuccess(DatabaseReference ref) {
                        showCompleteNtf(MainActivity.class, getApplicationContext(), group.groupName, ntfId, R.string.ntf_txt_change_group_img);
                    }
                };
                writer.update(CODE_SET_VALUE);
            }
        }, storageUtil, new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                showUploadingNtf(MainActivity.class, getApplicationContext(), taskSnapshot, fileName, ntfId);
            }
        });
    }

    @Override
    public void onFailure(@NonNull Exception e) {
        logStackTrace(e);
        toastHandler.post(new DisplayToast(R.string.error));
    }

    /**
     * @link "https://stackoverflow.com/questions/3955410/how-to-create-toast-from-intentservice-it-gets-stuck-on-the-screen/3955826#3955826"
     */
    private class DisplayToast implements Runnable{
        String mText;

        private DisplayToast(String text){
            mText = text;
        }

        private DisplayToast(@StringRes int strRes){
            mText = getString(strRes);
        }

        public void run(){
            Toast.makeText(getApplicationContext(), mText, Toast.LENGTH_LONG).show();
        }
    }
}
