package net.jejer.hipda.async;

import android.app.AlertDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.widget.Toast;

import net.jejer.hipda.okhttp.OkHttpHelper;
import net.jejer.hipda.utils.Constants;
import net.jejer.hipda.utils.HiUtils;
import net.jejer.hipda.utils.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.util.HashMap;
import java.util.Map;

public class PostSmsAsyncTask extends AsyncTask<String, Void, Void> {

    private Context mCtx;
    private String mUid;
    private String mUsername;
    private String mPmid;
    private String mSmsSubject;

    private String mFormhash;
    private int mStatus = Constants.STATUS_FAIL;
    private String mResult = "";
    private SmsPostListener mPostListenerCallback;
    private AlertDialog mDialog;

    public PostSmsAsyncTask(Context ctx, String pmid, String username, SmsPostListener postListener, AlertDialog dialog) {
        mCtx = ctx;
        mPmid = pmid;
        mUsername = username;
        mPostListenerCallback = postListener;
        mDialog = dialog;
    }

    @Override
    protected Void doInBackground(String... arg0) {
        String content = arg0[0];

        // fetch a new page and parse formhash
        String rsp_str = "";
        Boolean done = false;
        int retry = 0;
        do {
            try {
                rsp_str = OkHttpHelper.getInstance().get((HiUtils.SMSPreparePostUrl + mPmid));
                if (!TextUtils.isEmpty(rsp_str)) {
                    if (!LoginHelper.checkLoggedin(mCtx, rsp_str)) {
                        int status = new LoginHelper(mCtx, null).login();
                        if (status > Constants.STATUS_FAIL) {
                            break;
                        }
                    } else {
                        done = true;
                    }
                }
            } catch (Exception e) {
                mResult = OkHttpHelper.getErrorMessage(e);
            }
            retry++;
        } while (!done && retry < 3);

        if (done) {
            Document doc = Jsoup.parse(rsp_str);
            Elements formhashES = doc.select("input[name=formhash]");
            Elements smsSubjectES = doc.select("input#subject");
            if (formhashES.size() == 0) {
                mResult = "SMS send fail, can not get formhash.";
                return null;
            } else {
                mFormhash = formhashES.first().attr("value");
                if (mSmsSubject == "Re:"){
                    mSmsSubject = "新短消息";
                }
                mSmsSubject = "Re: " + smsSubjectES.first().attr("value");
            }
            // do post
            doPost(content);
        }

        return null;
    }

    private String doPost(String content) {

        String url;

        url = HiUtils.SMSPostByPmid;

        Map<String, String> post_param = new HashMap<>();
        post_param.put("formhash", mFormhash);
        post_param.put("subject", mSmsSubject);
        post_param.put("message", content);
        post_param.put("msgto", mUsername);

        String response = null;
        try {
            response = OkHttpHelper.getInstance().post(url, post_param);

            //response is in xml format
            if (TextUtils.isEmpty(response)) {
                mResult = "短消息发送失败 :  无返回结果";
            } else if (!response.contains("短消息发送成功。")) {
                String result = "";
                Document doc = Jsoup.parse(response, "", Parser.xmlParser());
                for (Element e : doc.select("root")) {
                    result = e.text();
                    if (result.contains("<"))
                        result = result.substring(0, result.indexOf("<"));
                }
                if (!TextUtils.isEmpty(result))
                    mResult = result;
                else
                    mResult = "短消息发送失败.";
            } else {
                mResult = "短消息发送成功.";
                mStatus = Constants.STATUS_SUCCESS;
            }
        } catch (Exception e) {
            Logger.e(e);
            mResult = "短消息发送失败 :  " + OkHttpHelper.getErrorMessage(e);
        }
        return response;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (mPostListenerCallback != null)
            mPostListenerCallback.onSmsPrePost();
        else
            Toast.makeText(mCtx, "正在发送...", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onPostExecute(Void avoid) {
        super.onPostExecute(avoid);
        if (mPostListenerCallback != null) {
            mPostListenerCallback.onSmsPostDone(mStatus, mResult, mDialog);
        } else {
            Toast.makeText(mCtx, mResult, Toast.LENGTH_LONG).show();
        }
    }

    public interface SmsPostListener {
        void onSmsPrePost();

        void onSmsPostDone(int status, String message, AlertDialog dialog);
    }

}
