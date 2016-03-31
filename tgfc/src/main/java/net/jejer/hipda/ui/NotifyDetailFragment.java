package net.jejer.hipda.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.okhttp.Request;

import net.jejer.hipda.R;
import net.jejer.hipda.async.PostSmsAsyncTask;
import net.jejer.hipda.bean.HiSettingsHelper;
import net.jejer.hipda.bean.SimpleListItemBean;
import net.jejer.hipda.glide.GlideHelper;
import net.jejer.hipda.okhttp.OkHttpHelper;
import net.jejer.hipda.utils.Constants;
import net.jejer.hipda.utils.HiParser;
import net.jejer.hipda.utils.Logger;
import net.jejer.hipda.utils.Utils;

public class NotifyDetailFragment extends BaseFragment implements PostSmsAsyncTask.SmsPostListener, View.OnTouchListener {

    public static final String ARG_AUTHOR = "AUTHOR";
    public static final String ARG_TITLE = "TITLE";
    public static final String ARG_TIME = "TIME";
    public static final String ARG_AVATAR_URL = "AVATARURL";
    public static final String ARG_INFO_URL = "INFOURL";

    private String mAuthor;
    private String mTitle;
    private String mTime;
    private String mAvatarUrl;
    private String mInfoUrl;
    private String mTid;
    private String mPid;

    private ImageView mAvatarView;
    private TextView mTitleView;
    private TextView mInfoView;
    private TextView mForumView;
    private TextView mTimeView;

    private HiProgressDialog smsPostProgressDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Logger.v("onCreate");
        setHasOptionsMenu(true);

        if (getArguments().containsKey(ARG_INFO_URL)) {
            mInfoUrl = getArguments().getString(ARG_INFO_URL);
        }

        if (getArguments().containsKey(ARG_AUTHOR)) {
            mAuthor = getArguments().getString(ARG_AUTHOR);
        }

        if (getArguments().containsKey(ARG_TITLE)) {
            mTitle = getArguments().getString(ARG_TITLE);
        }

        if (getArguments().containsKey(ARG_TIME)) {
            mTime = getArguments().getString(ARG_TIME);
        }

        if (getArguments().containsKey(ARG_AVATAR_URL)) {
            mAvatarUrl = getArguments().getString(ARG_AVATAR_URL);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.item_notify_detail, container, false);
        view.setClickable(false);

        view.setOnTouchListener(NotifyDetailFragment.this);

        mAvatarView = (ImageView) view.findViewById(R.id.iv_avatar);
        mTitleView = (TextView) view.findViewById(R.id.tv_title);
        
        mInfoView = (TextView) view.findViewById(R.id.tv_info);
        mInfoView.setText("正在获取信息...");
        mInfoView.setTextSize(HiSettingsHelper.getInstance().getPostTextSize());
        
        mForumView = (TextView) view.findViewById(R.id.tv_forum);
        mTimeView = (TextView) view.findViewById(R.id.tv_time);

        return view;
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            mInfoView.setVisibility(View.VISIBLE);
        }
        OkHttpHelper.getInstance().asyncGet(mInfoUrl, new NotifyDetailCallback());
        mInfoView.setOnClickListener(new OnDetailClickCallback());

    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        return true;
    }

    public class OnDetailClickCallback implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            FragmentUtils.showThread(getFragmentManager(), false, mTid, mTitle, -1, -1, mPid, -1);
        }
    }

    class NotifyDetailCallback implements OkHttpHelper.ResultCallback {
        @Override
        public void onError(Request request, Exception e) {
            Logger.e(e);
            mInfoView.setText("获取信息失败 : " + OkHttpHelper.getErrorMessage(e));
        }

        @Override
        public void onResponse(String response) {
            SimpleListItemBean item = HiParser.parseNotifyQuoteandDetail(response);
            if (item != null) {
                mTitleView.setTextSize(HiSettingsHelper.getInstance().getPostTextSize());
                mTitleView.setText(Utils.trim(mTitle));

                if (TextUtils.isEmpty(item.getInfo())) {
                    mInfoView.setVisibility(View.GONE);
                } else {
                    mInfoView.setVisibility(View.VISIBLE);
                    mInfoView.setText(Html.fromHtml(item.getInfo()));
                }

                if (TextUtils.isEmpty(item.getTime()) && TextUtils.isEmpty(item.getForum())) {
                    mTimeView.setVisibility(View.GONE);
                    mForumView.setVisibility(View.GONE);
                } else {
                    mTimeView.setVisibility(View.VISIBLE);
                    mForumView.setVisibility(View.VISIBLE);
                    mTimeView.setText(mTime);
                    mForumView.setText(mAuthor);
                }

                if (HiSettingsHelper.getInstance().isLoadAvatar()) {
                    mAvatarView.setVisibility(View.VISIBLE);
                    GlideHelper.loadAvatar(NotifyDetailFragment.this, mAvatarView, mAvatarUrl);
                } else {
                    mAvatarView.setVisibility(View.GONE);
                }

                mTid = item.getTid();
                mPid = item.getPid();


            } else {
                mInfoView.setText("解析信息失败, 请重试.");
            }
        }
    }

    @Override
    public void onSmsPrePost() {
        smsPostProgressDialog = HiProgressDialog.show(getActivity(), "正在发送...");
    }

    @Override
    public void onSmsPostDone(int status, final String message, AlertDialog dialog) {
        if (status == Constants.STATUS_SUCCESS) {
            smsPostProgressDialog.dismiss(message);
            if (dialog != null)
                dialog.dismiss();
        } else {
            smsPostProgressDialog.dismissError(message);
        }
    }

}
