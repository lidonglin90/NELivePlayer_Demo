package com.netease.neliveplayer.demo.activity.base;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.netease.neliveplayer.demo.R;
import com.netease.neliveplayer.demo.receiver.Observer;
import com.netease.neliveplayer.demo.receiver.PhoneCallStateObserver;
import com.netease.neliveplayer.demo.services.PlayerService;
import com.netease.neliveplayer.playerkit.sdk.PlayerManager;
import com.netease.neliveplayer.playerkit.sdk.VodPlayer;
import com.netease.neliveplayer.playerkit.sdk.VodPlayerObserver;
import com.netease.neliveplayer.playerkit.sdk.model.AutoRetryConfig;
import com.netease.neliveplayer.playerkit.sdk.model.CacheConfig;
import com.netease.neliveplayer.playerkit.sdk.model.DataSourceConfig;
import com.netease.neliveplayer.playerkit.sdk.model.MediaInfo;
import com.netease.neliveplayer.playerkit.sdk.model.StateInfo;
import com.netease.neliveplayer.playerkit.sdk.model.VideoBufferStrategy;
import com.netease.neliveplayer.playerkit.sdk.model.VideoOptions;
import com.netease.neliveplayer.playerkit.sdk.model.VideoScaleMode;
import com.netease.neliveplayer.playerkit.sdk.view.AdvanceSurfaceView;
import com.netease.neliveplayer.playerkit.sdk.view.AdvanceTextureView;
import com.netease.neliveplayer.sdk.NELivePlayer;
import com.netease.neliveplayer.sdk.model.NEAutoRetryConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MultiPlayerActivity extends Activity {

    public final static String TAG = MultiPlayerActivity.class.getSimpleName();

    private static final int SHOW_PROGRESS = 0x01;

    private ImageButton mPlayBack;

    private TextView mFileName; //文件名称

    private ImageView mAudioRemind; //播音频文件时提示

    private View mBuffer; //用于指示缓冲状态

    private ImageView mPauseButton;

    private ImageView mSetPlayerScaleButton;

    private ImageView mSnapshotButton;

    private ImageView mMuteButton;

    private SeekBar mProgressBar;

    private TextView mEndTime;

    private TextView mCurrentTime;

    private AdvanceTextureView textureView;

    private AdvanceSurfaceView textureViewMulti;

    private VodPlayer player;

    private VodPlayer playerMulti;

    private MediaInfo mediaInfo;

    private String mVideoPath; //文件路径

    private String mDecodeType;//解码类型，硬解或软解

    private String mMediaType; //媒体类型

    private boolean mHardware = true;

    private Uri mUri;

    private String mTitle;

    private boolean mIsFullScreen = false;

    private Handler mMainHandler;

    private boolean isPauseInBackgroud;

    private boolean isTimestampEnable;

    private boolean isAccurateSeek = true;

    private boolean isCache;

    private long pullIntervalTime = 100;

    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            long position;
            switch (msg.what) {
                case SHOW_PROGRESS:
                    position = setProgress();
                    msg = obtainMessage(SHOW_PROGRESS);
                    sendMessageDelayed(msg, 1000 - (position % 1000));
                    break;
            }
        }
    };

    private long setProgress() {
        if (player == null) {
            return 0;
        }
        int position = (int) player.getCurrentPosition();
        int duration = (int) player.getDuration();
        if (mProgressBar != null) {
            if (duration > 0) {
                Log.i(TAG, "setProgress,position:" + position + "duration:" + duration);
                long pos = 100L * position / duration;
                mProgressBar.setProgress((int) pos);
            }
        }
        if (mEndTime != null && duration > 0) {
            mEndTime.setText(stringForTime(duration));
        } else {
            mEndTime.setText("--:--:--");
        }
        if (mCurrentTime != null) {
            mCurrentTime.setText(stringForTime(position));
        }
        return position;
    }


    private View.OnClickListener mPlayBackOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            Log.i(TAG, "player_exit");
            finish();
        }
    };

    private SeekBar.OnSeekBarChangeListener mProgressSeekListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mHandler.removeMessages(SHOW_PROGRESS);

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            long seekTo = player.getDuration() * seekBar.getProgress() / 100;
            player.seekTo(seekTo);
            playerMulti.seekTo(seekTo);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.activity_multi);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //保持屏幕常亮
        PhoneCallStateObserver.getInstance().observeLocalPhoneObserver(localPhoneObserver, true);
        mMainHandler = new Handler(Looper.getMainLooper());
        parseIntent();
        initView();
        initPlayer();
    }

    private void parseIntent() {
        //接收MainActivity传过来的参数
        mMediaType = getIntent().getStringExtra("media_type");
        mDecodeType = getIntent().getStringExtra("decode_type");
        mVideoPath = getIntent().getStringExtra("videoPath");
        mUri = Uri.parse(mVideoPath);
        if (mMediaType != null && mMediaType.equals("localaudio")) { //本地音频文件采用软件解码
            mDecodeType = "software";
        }
        if (mDecodeType != null && mDecodeType.equals("hardware")) {
            mHardware = true;
        } else {
            mHardware = false;
        }

    }

    private void initView() {
        //这里支持使用SurfaceView和TextureView
        //surfaceView = findViewById(R.id.live_surface);
        textureView = findViewById(R.id.live_texture);
        textureViewMulti = findViewById(R.id.live_texture_multi);
        mPlayBack = findViewById(R.id.player_exit);//退出播放
        mPlayBack.getBackground().setAlpha(0);
        mFileName = findViewById(R.id.file_name);
        if (mUri != null) { //获取文件名，不包括地址
            List<String> paths = mUri.getPathSegments();
            String name = paths == null || paths.isEmpty() ? "null" : paths.get(paths.size() - 1);
            setFileName(name);
        }
        mAudioRemind = findViewById(R.id.audio_remind);
        if (mMediaType != null && mMediaType.equals("localaudio")) {
            mAudioRemind.setVisibility(View.VISIBLE);
        } else {
            mAudioRemind.setVisibility(View.INVISIBLE);
        }
        mBuffer = findViewById(R.id.buffering_prompt);
        mPauseButton = findViewById(R.id.mediacontroller_play_pause); //播放暂停按钮
        mPauseButton.setImageResource(R.mipmap.player_control_play);
        mPauseButton.setOnClickListener(mPauseListener);
        mPlayBack.setOnClickListener(mPlayBackOnClickListener); //监听退出播放的事件响应
        mSnapshotButton = findViewById(R.id.snapShot);  //截图按钮
        mMuteButton = findViewById(R.id.video_player_mute);  //静音按钮
        mProgressBar = findViewById(R.id.mediacontroller_seekbar);  //进度条
        mProgressBar.setOnSeekBarChangeListener(mProgressSeekListener);
        mEndTime = findViewById(R.id.mediacontroller_time_total); //总时长
        mEndTime.setText("--:--:--");
        mCurrentTime = findViewById(R.id.mediacontroller_time_current); //当前播放位置
        mCurrentTime.setText("--:--:--");
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        mSnapshotButton = findViewById(R.id.snapShot);  //截图按钮
        mSnapshotButton.setOnClickListener(mSnapShotListener);
        mSetPlayerScaleButton = findViewById(R.id.video_player_scale);  //画面显示模式按钮
        mSetPlayerScaleButton.setOnClickListener(mSetPlayerScaleListener);


    }

    private View.OnClickListener mPauseListener = new View.OnClickListener() {

        public void onClick(View v) {
            if (player.isPlaying()) {
                mPauseButton.setImageResource(R.mipmap.player_control_pause);
                showToast("暂停播放");
                player.pause();
                playerMulti.pause();
            } else {
                mPauseButton.setImageResource(R.mipmap.player_control_play);
                showToast("继续播放");
                player.start();
                //playerMulti.start();
            }
        }
    };


    private void initPlayer() {
        VideoOptions options = new VideoOptions();
        options.isSyncOpen = true;  // 开启SEI时间戳同步
        options.bufferStrategy = VideoBufferStrategy.ANTI_JITTER;
        options.hardwareDecode = mHardware;
        /**
         * isPlayLongTimeBackground 控制退到后台或者锁屏时是否继续播放，开发者可根据实际情况灵活开发,我们的示例逻辑如下：
         * isPlayLongTimeBackground 为 false，使用软件编码或者硬件解码，点播进入后台暂停，进入前台恢复播放
         * isPlayLongTimeBackground 为 true，使用软件编码，点播进入后台不做处理，继续播放,使用硬件解码，点播进入后台统一停止播放，进入前台的话重新拉流播放
         *
         */
        options.isPlayLongTimeBackground = !isPauseInBackgroud;
        options.isAccurateSeek = isAccurateSeek;
        options.isAutoStart = true;
        DataSourceConfig dataSourceConfig = new DataSourceConfig();
        dataSourceConfig.cacheConfig = new CacheConfig(isCache, getCachePath());
        options.dataSourceConfig = dataSourceConfig;
        player = PlayerManager.buildVodPlayer(this, mVideoPath, options);
        playerMulti = PlayerManager.buildVodPlayer(this, mVideoPath, options);
        if (playerMulti == null) {
            finish();
            return;
        }
        intentToStartBackgroundPlay();
        start();
        player.setupRenderView(textureView, VideoScaleMode.FIT);
        playerMulti.setupRenderView(textureViewMulti, VideoScaleMode.FIT);
    }
    private String getCachePath() {
        return this.getCacheDir().toString();
    }

    public void setFileName(String name) { //设置文件名并显示出来
        mTitle = name;
        if (mFileName != null) {
            mFileName.setText(mTitle);
        }
        mFileName.setGravity(Gravity.CENTER);
    }

    private NEAutoRetryConfig.OnRetryListener onRetryListener = new NEAutoRetryConfig.OnRetryListener() {

        @Override
        public void onRetry(int what, int extra) {
            mBuffer.setVisibility(View.INVISIBLE);
            showToast("开始重试，错误类型：" + what + "，附加信息：" + extra);
        }
    };

    private void start() {
        AutoRetryConfig autoRetryConfig = new AutoRetryConfig();
        autoRetryConfig.count = 0;
        autoRetryConfig.retryListener = onRetryListener;
        player.setAutoRetryConfig(autoRetryConfig);
        player.registerPlayerObserver(playerObserver, true);
        player.start();
        if (isTimestampEnable) {
            player.registerPlayerCurrentPositionListener(pullIntervalTime, mOnCurrentPositionListener, true);
            player.registerPlayerCurrentRealTimestampListener(pullIntervalTime, mOnCurrentRealTimeListener, true);
            player.registerPlayerCurrentSyncTimestampListener(pullIntervalTime, mOnCurrentSyncTimestampListener, true);
        }
        playerMulti.setAutoRetryConfig(autoRetryConfig);
        playerMulti.registerPlayerObserver(multiPlayerObserver, true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");

    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        if (player != null) {
            player.onActivityResume(false);
        }
        if (playerMulti != null) {
            playerMulti.onActivityResume(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");

    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
        enterBackgroundPlay();
        if (player != null) {
            player.onActivityStop(false);
        }
        if (playerMulti != null) {
            playerMulti.onActivityStop(false);
        }
    }

    @Override
    public void onBackPressed() {
        Log.i(TAG, "onBackPressed");
        super.onBackPressed();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        mHandler.removeCallbacksAndMessages(null);
        mMainHandler.removeCallbacksAndMessages(null);
        PhoneCallStateObserver.getInstance().observeLocalPhoneObserver(localPhoneObserver, false);
        if (player != null) {
            player.registerPlayerObserver(playerObserver, false);
            player.setupRenderView(null, VideoScaleMode.NONE);
            textureView.releaseSurface();
            textureView = null;
            player.stop();
        }
        if (playerMulti != null) {
            playerMulti.registerPlayerObserver(multiPlayerObserver, false);
            playerMulti.setupRenderView(null, VideoScaleMode.NONE);
            textureViewMulti = null;
            playerMulti.stop();
        }
    }

    /**
     * default impl
     */
    public class VodPlayerObserverAdapter implements VodPlayerObserver {

        @Override
        public void onCurrentPlayProgress(long currentPosition, long duration, float percent, long cachedPosition) {
        }

        @Override
        public void onSeekCompleted() {
            Log.i(TAG, "onSeekCompleted");
            mHandler.sendEmptyMessageDelayed(SHOW_PROGRESS, 1000);
        }

        @Override
        public void onCompletion() {
        }

        @Override
        public void onAudioVideoUnsync() {
            showToast("音视频不同步");

        }

        @Override
        public void onNetStateBad() {
        }

        @Override
        public void onDecryption(int ret) {
            Log.i(TAG, "onDecryption ret = " + ret);
        }

        @Override
        public void onPreparing() {
        }

        @Override
        public void onPrepared(MediaInfo info) {
            mediaInfo = info;
        }

        @Override
        public void onError(int code, int extra) {
            AlertDialog.Builder build = new AlertDialog.Builder(MultiPlayerActivity.this);
            build.setTitle("播放错误").setMessage("错误码：" + code).setPositiveButton("确定", null).setCancelable(false).show();
        }

        @Override
        public void onFirstVideoRendered() {
            showToast("视频第一帧已解析");

        }

        @Override
        public void onFirstAudioRendered() {
            //            showToast("音频第一帧已解析");
        }

        @Override
        public void onBufferingStart() {
            mBuffer.setVisibility(View.VISIBLE);

        }

        @Override
        public void onBufferingEnd() {
            mBuffer.setVisibility(View.GONE);

        }

        @Override
        public void onBuffering(int percent) {
            Log.d(TAG, "缓冲中..." + percent + "%");
            mProgressBar.setSecondaryProgress(percent);
        }

        @Override
        public void onVideoDecoderOpen(int value) {
        }

        @Override
        public void onStateChanged(StateInfo stateInfo) {
        }

        @Override
        public void onHttpResponseInfo(int code, String header) {
            Log.i(TAG, "onHttpResponseInfo,code:" + code + " header:" + header);
        }
    }

    private VodPlayerObserver playerObserver = new VodPlayerObserverAdapter() {

        //onPrepared
        @Override
        public void onPrepared(MediaInfo info) {
            super.onPrepared(info);
            // 两个视频同步时间戳播放
            playerMulti.syncClockTo(player);
            playerMulti.start();
        }
    };

    private VodPlayerObserver multiPlayerObserver = new VodPlayerObserverAdapter();

    private void showToast(String msg) {
        Log.d(TAG, "showToast" + msg);
        try {
            Toast.makeText(MultiPlayerActivity.this, msg, Toast.LENGTH_SHORT).show();
        } catch (Throwable th) {
            th.printStackTrace(); // fuck oppo
        }
    }

    private static String stringForTime(long position) {
        int totalSeconds = (int) ((position / 1000.0) + 0.5);
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds).toString();
    }

    public void getSnapshot() {
        if (mediaInfo == null) {
            Log.d(TAG, "mediaInfo is null,截图不成功");
            showToast("截图不成功");
        } else if (mediaInfo.getVideoDecoderMode().equals("MediaCodec")) {
            Log.d(TAG, "hardware decoder unsupport snapshot");
            showToast("截图不支持硬件解码");
        } else {
            Bitmap bitmap = player.getSnapshot();
            String picName = "/sdcard/NESnapshot" + System.currentTimeMillis() + ".jpg";
            File f = new File(picName);
            try {
                f.createNewFile();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            FileOutputStream fOut = null;
            try {
                fOut = new FileOutputStream(f);
                if (picName.substring(picName.lastIndexOf(".") + 1, picName.length()).equals("jpg")) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
                } else if (picName.substring(picName.lastIndexOf(".") + 1, picName.length()).equals("png")) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
                }
                fOut.flush();
                fOut.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            showToast("截图成功");
        }
    }

    private View.OnClickListener mSnapShotListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (mMediaType.equals("localaudio") || mHardware) {
                if (mMediaType.equals("localaudio")) {
                    showToast("音频播放不支持截图！");
                } else if (mHardware) {
                    showToast("硬件解码不支持截图！");
                }
                return;
            }
            getSnapshot();
        }
    };

    private View.OnClickListener mSetPlayerScaleListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            player.setupRenderView(null, VideoScaleMode.NONE);
            if (mIsFullScreen) {
                mSetPlayerScaleButton.setImageResource(R.mipmap.player_control_scale01);
                mIsFullScreen = false;
                player.setupRenderView(textureView, VideoScaleMode.FIT);

            } else {
                mSetPlayerScaleButton.setImageResource(R.mipmap.player_control_scale02);
                mIsFullScreen = true;
                player.setupRenderView(textureView, VideoScaleMode.FULL);
            }
        }
    };

    private NELivePlayer.OnCurrentPositionListener mOnCurrentPositionListener = new NELivePlayer.OnCurrentPositionListener() {

        @Override
        public void onCurrentPosition(long position) {
            Log.v(TAG, "OnCurrentPositionListener,onCurrentPosition:" + position);

        }
    };

    /**
     * 时间戳回调
     */
    private NELivePlayer.OnCurrentRealTimeListener mOnCurrentRealTimeListener = new NELivePlayer.OnCurrentRealTimeListener() {

        @Override
        public void onCurrentRealTime(long realTime) {
            Log.v(TAG, "OnCurrentRealTimeListener,onCurrentRealTime:" + realTime);

        }
    };


    private NELivePlayer.OnCurrentSyncTimestampListener mOnCurrentSyncTimestampListener = new NELivePlayer.OnCurrentSyncTimestampListener() {

        @Override
        public void onCurrentSyncTimestamp(long timestamp) {
            Log.v(TAG, "OnCurrentSyncTimestampListener,onCurrentSyncTimestamp:" + timestamp);

        }
    };

    /**
     * 处理service后台播放逻辑
     */
    private void intentToStartBackgroundPlay() {
        if (isPauseInBackgroud || (!isPauseInBackgroud && !mHardware)) {
            PlayerService.intentToStart(this);
        }
    }

    private void intentToStopBackgroundPlay() {
        if (isPauseInBackgroud || (!isPauseInBackgroud && !mHardware)) {
            PlayerService.intentToStop(this);
            player = null;
        }
    }


    public void enterBackgroundPlay() {
        if (isPauseInBackgroud || (!isPauseInBackgroud && !mHardware)) {
            PlayerService.setMediaPlayer(player);
        }
    }

    public void stopBackgroundPlay() {
        if (isPauseInBackgroud || (!isPauseInBackgroud && !mHardware)) {
            PlayerService.setMediaPlayer(null);
        }
    }

    //处理与电话逻辑
    private Observer<Integer> localPhoneObserver = new Observer<Integer>() {

        @Override
        public void onEvent(Integer phoneState) {
            if (phoneState == TelephonyManager.CALL_STATE_IDLE) {
                player.start();
            } else if (phoneState == TelephonyManager.CALL_STATE_RINGING) {
                player.pause();
            } else {
                Log.i(TAG, "localPhoneObserver onEvent " + phoneState);
            }

        }
    };
}

