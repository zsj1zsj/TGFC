package net.jejer.hipda.async;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;

import net.jejer.hipda.R;
import net.jejer.hipda.bean.HiSettingsHelper;
import net.jejer.hipda.bean.PostBean;
import net.jejer.hipda.bean.PrePostInfoBean;
import net.jejer.hipda.okhttp.OkHttpHelper;
import net.jejer.hipda.utils.Constants;
import net.jejer.hipda.utils.CursorUtils;
import net.jejer.hipda.utils.FormFile;
import net.jejer.hipda.utils.HiUtils;
import net.jejer.hipda.utils.HttpUtils;
import net.jejer.hipda.utils.ImageFileInfo;
import net.jejer.hipda.utils.Logger;
import net.jejer.hipda.utils.Utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

public class PostAsyncTask extends AsyncTask<PostBean, Void, Void> {

    public final static int MAX_QUALITY = 90;
    private static final int THUMB_SIZE = 128;

    private final static int MAX_PIXELS = 1200 * 1200; //file with this resolution, it's size should match to MAX_IMAGE_FILE_SIZE
    public final static int MAX_IMAGE_FILE_SIZE = 400 * 1024; // max file size 400K
    public final static int MAX_SPECIAL_FILE_SIZE = 500 * 1024; // max upload file size : 8M

    private String mMessage = "";
    private String mCurrentFileName = "";
    private String mCurrentFileType = "";
    private Bitmap mThumb;

    public static final int MODE_REPLY_THREAD = 0;
    public static final int MODE_REPLY_POST = 1;
    public static final int MODE_QUOTE_POST = 2;
    public static final int MODE_NEW_THREAD = 3;
    public static final int MODE_QUICK_REPLY = 4;
    public static final int MODE_EDIT_POST = 5;
    public static final int MODE_RATING_POST = 6;

    private int mMode;
    private String mResult;
    private int mStatus = Constants.STATUS_FAIL;
    private Context mCtx;
    private PrePostInfoBean mInfo;

    private PostListener mPostListenerCallback;
    private String mTid;
    private String mTitle;
    private String mFloor;

    public PostAsyncTask(Context ctx, int mode, PrePostInfoBean info, PostListener postListenerCallback) {
        mCtx = ctx;
        mMode = mode;
        mInfo = info;
        mPostListenerCallback = postListenerCallback;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (mPostListenerCallback != null)
            mPostListenerCallback.onPrePost();
    }

    @Override
    protected Void doInBackground(PostBean... postBeans) {

        PostBean postBean = postBeans[0];
        String replyText = postBean.getContent();
        String tid = postBean.getTid();
        String pid = postBean.getPid();
        String fid = postBean.getFid();
        String floor = postBean.getFloor();
        String subject = postBean.getSubject();
        String typeid = postBean.getTypeid();
        Map<Uri, String> images = postBean.getImages();

        String page = postBean.getPage();
        String score = postBean.getScore();
        String reason = postBean.getReason();

        int count = 0;
        while (mInfo == null && count < 3) {
            count++;
            mInfo = new PrePostAsyncTask(mCtx, null, mMode).doInBackground(postBean);
        }

        if (!TextUtils.isEmpty(floor) && TextUtils.isDigitsOnly(floor))
            mFloor = floor;

        if (mMode == MODE_RATING_POST)
        {

            String url = HiUtils.RatingSubmit + "&ratesubmit=yes";
            // do send
            doRatingPost(url, tid, pid, page, score, reason);
            return null;
        }

        if (mMode != MODE_EDIT_POST) {
            String model_text = Build.MODEL.toLowerCase().indexOf(Build.MANUFACTURER.toLowerCase()) < 0 ?
                    ", " + Build.MANUFACTURER + " " + Build.MODEL :
                    ", " + Build.MODEL;
            String platform_text = HiSettingsHelper.getInstance().isHiddenPlatform() ?
                    "[color=DarkRed][size=2] posted by tgfc·ng [/size][/color]\r\n" :
                    "[color=DarkRed][size=2] posted by tgfc·ng" + model_text + "[/size][/color]\r\n";
            replyText = platform_text + replyText;
            String tail_text = HiSettingsHelper.getInstance().getTailText();
            if (!tail_text.isEmpty() && HiSettingsHelper.getInstance().isAddTail()) {
                String tail_url = HiSettingsHelper.getInstance().getTailUrl();
                if (!tail_url.isEmpty()) {
                    if ((!tail_url.startsWith("http")) && (!tail_url.startsWith("https"))) {
                        tail_url = "http://" + tail_url;
                    }
                    replyText = replyText + "  [url=" + tail_url + "][size=1]" + tail_text + "[/size][/url]";
                } else {
                    replyText = replyText + "  [size=1]" + tail_text + "[/size]";
                }
            }
        }

        String url = HiUtils.ReplyUrl + tid + "&replysubmit=yes";
        // do send
        switch (mMode) {
            case MODE_REPLY_THREAD:
            case MODE_QUICK_REPLY:
                doPost(url, replyText, null, null, images);
                break;
            case MODE_REPLY_POST:
            case MODE_QUOTE_POST:
                doPost(url, mInfo.getText() + "\n\n    " + replyText, null, null, images);
                break;
            case MODE_NEW_THREAD:
                url = HiUtils.NewThreadUrl + fid + "&typeid=" + typeid + "&topicsubmit=yes";
                doPost(url, replyText, subject, null, images);
                break;
            case MODE_EDIT_POST:
                url = HiUtils.EditUrl + "&extra=&editsubmit=yes&mod=&editsubmit=yes" + "&fid=" + fid + "&tid=" + tid + "&pid=" + pid + "&page=1";
                doPost(url, replyText, subject, typeid, images);
                break;
        }

        return null;
    }

    @Override
    protected void onPostExecute(Void avoid) {
        PostBean postBean = new PostBean();
        postBean.setSubject(mTitle);
        postBean.setFloor(mFloor);
        postBean.setTid(mTid);
        if (mPostListenerCallback != null)
            mPostListenerCallback.onPostDone(mMode, mStatus, mResult, postBean);
    }

    private void doPost(String url, String replyText, String subject, String typeid, Map<Uri, String> images) {

        String formhash = mInfo != null ? mInfo.getFormhash() : null;

        if (TextUtils.isEmpty(formhash)) {
            mResult = "发表失败，无法获取必要信息 ！";
            mStatus = Constants.STATUS_FAIL;
            return;
        }

        Map<String, String> post_param = new HashMap<>();
        post_param.put("formhash", formhash);
        post_param.put("posttime", String.valueOf(System.currentTimeMillis()));
        post_param.put("wysiwyg", "0");
        post_param.put("checkbox", "0");
        post_param.put("message", replyText);

        if (mMode == MODE_NEW_THREAD) {
            post_param.put("subject", subject);
            post_param.put("attention_add", "1");
            mTitle = subject;
        } else if (mMode == MODE_EDIT_POST) {
            if (!TextUtils.isEmpty(subject)) {
                post_param.put("subject", subject);
                mTitle = subject;
                if (!TextUtils.isEmpty(typeid)) {
                    post_param.put("typeid", typeid);
                }
            }
        }

        if (mMode == MODE_QUOTE_POST
                || mMode == MODE_REPLY_POST) {
            String noticeauthor = mInfo.getNoticeauthor();
            String noticeauthormsg = mInfo.getNoticeauthormsg();
            String noticetrimstr = mInfo.getNoticetrimstr();
            if (!TextUtils.isEmpty(noticeauthor)) {
                post_param.put("noticeauthor", noticeauthor);
                post_param.put("noticeauthormsg", Utils.nullToText(noticeauthormsg));
                post_param.put("noticetrimstr", Utils.nullToText(noticetrimstr));
            }
        }

        String rsp_str;
        try {
            if (images != null) {
                FormFile[] files = new FormFile[images.size()];
                int i = 0;
                for(Uri uri : images.keySet()){
                    mCurrentFileName = "";
                    mCurrentFileType = "";

                    ImageFileInfo imageFileInfo = CursorUtils.getImageFileInfo(mCtx, uri);
                    mCurrentFileName = imageFileInfo.getFileName();
                    mCurrentFileType = imageFileInfo.getMime();

                    ByteArrayOutputStream baos = compressImage(uri, imageFileInfo);
                    if (baos == null) {
                        return;
                    }

                    files[i]= new FormFile(mCurrentFileName, baos.toByteArray(), String.valueOf(i+1), mCurrentFileType);
                    i++;
                }
                rsp_str = PostFilesHelper.post( url, post_param, files);
//                rsp_str = OkHttpHelper.getInstance().postImages(url, post_param, images);
            } else {
                rsp_str = OkHttpHelper.getInstance().post(url, post_param);
            }

            //when success, okhttp will follow 302 redirect get the page content
            if (!TextUtils.isEmpty(rsp_str)) {
                String tid = "";
                if (rsp_str.contains("tid = parseInt('")) {
                    tid = HttpUtils.getMiddleString(rsp_str, "tid = parseInt('", "'");
                }
                if (!TextUtils.isEmpty(tid)
                        && TextUtils.isDigitsOnly(tid)
                        && Integer.parseInt(tid) > 0
                        && !rsp_str.contains("alert_info")) {
                    mTid = tid;
                    mResult = "发表成功!";
                    mStatus = Constants.STATUS_SUCCESS;
                } else {
                    Logger.e(rsp_str);
                    mResult = "发表失败! ";
                    mStatus = Constants.STATUS_FAIL;

                    Document doc = Jsoup.parse(rsp_str);
                    Elements error = doc.select("div.alert_info");
                    if (error != null && error.size() > 0) {
                        mResult += "\n" + error.text();
                    }
                }
            } else {
                mResult = "发表失败，无返回结果! ";
                mStatus = Constants.STATUS_FAIL;
            }

        } catch (Exception e) {
            Logger.e(e);
            mResult = "发表失败 : " + OkHttpHelper.getErrorMessage(e);
            mStatus = Constants.STATUS_FAIL;
        }

    }

    private void doRatingPost(String url, String tid, String pid, String page, String score, String reason) {

        String formhash = mInfo != null ? mInfo.getFormhash() : null;

        if (TextUtils.isEmpty(formhash)) {
//            mResult = "评分失败，无法获取必要信息 ！";
            mResult = mInfo.getText();
            mStatus = Constants.STATUS_FAIL;
            return;
        }

        int ratingAmount = Integer.parseInt(mInfo != null ? mInfo.getRatingAmount() : null) - Math.abs(Integer.parseInt(score));


        Map<String, String> post_param = new HashMap<>();
        post_param.put("formhash", formhash);
        post_param.put("score4", score);
        post_param.put("selectreason", reason);
        post_param.put("reason", reason);
        post_param.put("tid", tid);
        post_param.put("pid", pid);
        post_param.put("page", page);

        Logger.v(post_param.toString());

        String rsp_str;
        try {
            rsp_str = OkHttpHelper.getInstance().post(url, post_param);

            //when success, okhttp will follow 302 redirect get the page content
            if (!TextUtils.isEmpty(rsp_str)) {
                if (rsp_str.contains(mCtx.getString(R.string.rating_success))) {
                    Logger.v("Rating success!");
                    mTid = tid;
                    mResult = "评分成功,您今日还能使用 " + ratingAmount + " 评分。";
                    mStatus = Constants.STATUS_SUCCESS;
                } else if (rsp_str.contains(mCtx.getString(R.string.rating_fail))) {
                    Logger.e("Rating FAIL");
                    Logger.e(rsp_str);
                    mResult = "对不起，您最近 24 小时评分数超过限制! ";
                    mStatus = Constants.STATUS_FAIL;
                }
            } else {
                mResult = "评分失败，无返回结果! ";
                mStatus = Constants.STATUS_FAIL;
            }

        } catch (Exception e) {
            Logger.e(e);
            mResult = "评分失败 : " + OkHttpHelper.getErrorMessage(e);
            mStatus = Constants.STATUS_FAIL;
        }

    }

    public interface PostListener {
        void onPrePost();

        void onPostDone(int mode, int status, String message, PostBean postBean);
    }

    private ByteArrayOutputStream compressImage(Uri uri, ImageFileInfo imageFileInfo) {

        if (imageFileInfo.isGif()
                && imageFileInfo.getFileSize() > MAX_SPECIAL_FILE_SIZE) {
            mMessage = "GIF图片大小不能超过" + Utils.toSizeText(MAX_SPECIAL_FILE_SIZE);
            return null;
        }

        Bitmap bitmap;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(mCtx.getContentResolver(), uri);
        } catch (Exception e) {
            Logger.v("Exception", e);
            mMessage = "无法获取图片 : " + e.getMessage();
            return null;
        }

        //gif or very long image or small images etc
        if (isDirectUploadable(imageFileInfo)) {
            mThumb = ThumbnailUtils.extractThumbnail(bitmap, THUMB_SIZE, THUMB_SIZE);
            bitmap.recycle();
            return readFileToStream(imageFileInfo.getFilePath());
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, MAX_QUALITY, baos);

        if (baos.size() <= MAX_IMAGE_FILE_SIZE) {
            mThumb = ThumbnailUtils.extractThumbnail(bitmap, THUMB_SIZE, THUMB_SIZE);
            bitmap.recycle();
            bitmap = null;
            return baos;
        }
        bitmap.recycle();
        bitmap = null;

        ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(isBm, null, opts);

        int width = opts.outWidth;
        int height = opts.outHeight;

        //inSampleSize is needed to avoid OOM
        int be = (int) (Math.max(width, height) * 1.0 / 1500);
        if (be <= 0)
            be = 1; //be=1表示不缩放
        BitmapFactory.Options newOpts = new BitmapFactory.Options();
        newOpts.inJustDecodeBounds = false;
        newOpts.inSampleSize = be;

        isBm = new ByteArrayInputStream(baos.toByteArray());
        Bitmap newbitmap = BitmapFactory.decodeStream(isBm, null, newOpts);

        width = newbitmap.getWidth();
        height = newbitmap.getHeight();

        //scale bitmap so later compress could run less times, once is the best result
        //rotate if needed
        if ((baos.size() > MAX_IMAGE_FILE_SIZE
                && width * height > MAX_PIXELS)
                || imageFileInfo.getOrientation() > 0) {

            float scale = 1.0f;
            if (width * height > MAX_PIXELS) {
                scale = (float) Math.sqrt(MAX_PIXELS * 1.0 / (width * height));
            }

            Matrix matrix = new Matrix();
            if (imageFileInfo.getOrientation() > 0)
                matrix.postRotate(imageFileInfo.getOrientation());
            matrix.postScale(scale, scale);

            Bitmap scaledBitmap = Bitmap.createBitmap(newbitmap, 0, 0, newbitmap.getWidth(),
                    newbitmap.getHeight(), matrix, true);

            newbitmap.recycle();
            newbitmap = scaledBitmap;
        }

        int quality = MAX_QUALITY;
        baos.reset();
        newbitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        while (baos.size() > MAX_IMAGE_FILE_SIZE) {
            quality -= 10;
            if (quality <= 0) {
                mMessage = "无法压缩图片至指定大小 " + Utils.toSizeText(MAX_IMAGE_FILE_SIZE);
                return null;
            }
            baos.reset();
            newbitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        }

        mThumb = ThumbnailUtils.extractThumbnail(newbitmap, THUMB_SIZE, THUMB_SIZE);
        newbitmap.recycle();
        newbitmap = null;

        System.gc();
        return baos;
    }

    private boolean isDirectUploadable(ImageFileInfo imageFileInfo) {
        long fileSize = imageFileInfo.getFileSize();
        int w = imageFileInfo.getWidth();
        int h = imageFileInfo.getHeight();

        if (TextUtils.isEmpty(imageFileInfo.getFilePath()))
            return false;

        if (imageFileInfo.getOrientation() > 0)
            return false;

        //gif image
        if (imageFileInfo.isGif() && fileSize <= MAX_SPECIAL_FILE_SIZE)
            return true;

        //very long or wide image
        if (w > 0 && h > 0 && fileSize <= MAX_SPECIAL_FILE_SIZE) {
            if (Math.max(w, h) * 1.0 / Math.min(w, h) >= 3)
                return true;
        }

        //normal image
        return fileSize <= MAX_IMAGE_FILE_SIZE;
    }

    private static ByteArrayOutputStream readFileToStream(String file) {
        FileInputStream fileInputStream = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            fileInputStream = new FileInputStream(file);
            int readedBytes;
            byte[] buf = new byte[1024];
            while ((readedBytes = fileInputStream.read(buf)) > 0) {
                bos.write(buf, 0, readedBytes);
            }
            return bos;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (fileInputStream != null)
                    fileInputStream.close();
            } catch (Exception ignored) {

            }
        }
    }
}
