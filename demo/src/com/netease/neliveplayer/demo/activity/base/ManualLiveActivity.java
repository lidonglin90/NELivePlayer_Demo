package com.netease.neliveplayer.demo.activity.base;

import android.content.res.Resources;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.netease.neliveplayer.demo.R;
import com.netease.neliveplayer.playerkit.common.log.LogUtil;
import com.netease.neliveplayer.playerkit.sdk.LivePlayer;
import com.netease.neliveplayer.proxy.gslb.GlsbSession;
import com.netease.neliveplayer.proxy.gslb.NEGslbResultListener;
import com.netease.neliveplayer.proxy.gslb.NEGslbServerModel;

import java.util.List;

/**
 * Created by hzsunyj on 2019/4/10.
 */
public class ManualLiveActivity extends LiveActivity {

    public static final String TAG = "ManualLiveActivity";

    private GlsbSession session;

    private List<NEGslbServerModel> serverModels;

    private View addressLayout;

    private LinearLayout addressContainer;

    private View preload;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_manual_live;
    }

    @Override
    protected void findViews() {
        super.findViews();
        addressLayout = findViewById(R.id.address_layout);
        addressContainer = findViewById(R.id.address_container);
        preload = findViewById(R.id.preload);
        preload.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                int ordinal = player.getCurrentState().getState().ordinal();
                if (ordinal < LivePlayer.STATE.PREPARED.ordinal() || ordinal > LivePlayer.STATE.PLAYING.ordinal()) {
                    showToast("player not prepared or pause");
                    return;
                }
                if (addressLayout.getVisibility() == View.VISIBLE) {
                    addressLayout.setVisibility(View.GONE);
                } else {
                    onQuery();
                }
            }
        });
    }

    protected void onQuery() {
        player.queryPreloadUrlResult(mVideoPath, new NEGslbResultListener() {

            @Override
            public void onResult(GlsbSession session, List<NEGslbServerModel> serverModels) {
                ManualLiveActivity.this.session = session;
                ManualLiveActivity.this.serverModels = serverModels;
                showAddressList(serverModels);
            }
        });
    }

    private void showAddressList(List<NEGslbServerModel> serverModels) {
        if (serverModels == null || serverModels.size() == 0) {
            showToast("地址获取失败");
            LogUtil.e(TAG, "server models fetch error");
            return;
        }
        addressLayout.setVisibility(View.VISIBLE);
        addressContainer.removeAllViews();
        int index = 0;
        for (NEGslbServerModel model : serverModels) {
            buildItem(model, index++);
        }
    }
    private void buildItem(NEGslbServerModel model, final int index) {
        if (model == null) {
            return;
        }
        if (TextUtils.isEmpty(model.url)) {
            return;
        }
        TextView textView = new TextView(this);
        textView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                         ViewGroup.LayoutParams.WRAP_CONTENT);
        params.height = dip2px(45);
        textView.setLayoutParams(params);
        textView.setText(model.url);
        textView.setTextColor(getResources().getColor(R.color.white));
        textView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                addressLayout.setVisibility(View.GONE);
                player.switchWithGslbResult(session, serverModels.get(index));
            }
        });
        addressContainer.addView(textView);
    }

    public int dip2px(float dipValue) {
        final float scale = Resources.getSystem().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }
}
