package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ArchivedStickerSetCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


public class AvailableReactionsView extends FrameLayout {
    private RectF backRect = new RectF();
    private Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF shadowRect = new RectF();
    private Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private OnClickListener onClickListener;
    private RecyclerListView listView;
    private ChatActivity chatActivity;
    private HashMap<String, TLRPC.TL_availableReaction> availableReactions;
    private ArrayList<String> availableReactionsString;

    public interface OnClickListener {
        void OnClick(String reaction);
    }

    public AvailableReactionsView(@NonNull Activity context, ChatActivity chatActivity) {
        super(context);

        this.chatActivity = chatActivity;
        availableReactions = chatActivity.getMessagesController().availableReactions;

        if (chatActivity.chatInfo != null && !chatActivity.chatInfo.available_reactions.isEmpty()) {
            availableReactionsString = new ArrayList<>(chatActivity.chatInfo.available_reactions);
        } else {
            availableReactionsString = new ArrayList<>();

            for (Map.Entry<String, TLRPC.TL_availableReaction> kvp : availableReactions.entrySet()) {
                availableReactionsString.add(kvp.getKey());
            }
        }

        backPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));

        setBackgroundColor(0x0);

        listView = new RecyclerListView(context);
        listView.setFocusable(true);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        listView.setAdapter(new ReactionsListAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (onClickListener != null) {
                onClickListener.OnClick(availableReactionsString.get(position));
            }
        });

        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 7, 0, 7, 0));
    }

    private class ReactionsListAdapter extends RecyclerListView.SelectionAdapter {
        private Context mContext;

        public ReactionsListAdapter(Context mContext) {
            this.mContext = mContext;
        }

        @Override
        public int getItemCount() {
            return availableReactionsString.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;

            view = new ReactionCell(mContext);

            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof ReactionCell) {
                ReactionCell reactionCell = (ReactionCell) holder.itemView;

                reactionCell.setReaction(availableReactions.get(availableReactionsString.get(position)));
            }
        }
    }

    public void setOnItemClickListener(OnClickListener listener) {
        onClickListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        shadowPaint.setColor(Theme.getColor(Theme.key_dialogTextBlack));
        shadowPaint.setAlpha((int) (255 * 0.175));

        backRect.set(2, 2, getMeasuredWidth() - 4, getMeasuredHeight() - 4);
        shadowRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());

        canvas.drawRoundRect(shadowRect, AndroidUtilities.dp(50), AndroidUtilities.dp(50), shadowPaint);
        canvas.drawRoundRect(backRect, AndroidUtilities.dp(50), AndroidUtilities.dp(50), backPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(280), MeasureSpec.AT_MOST), heightMeasureSpec);
    }

    protected class ReactionCell extends FrameLayout {
        private BackupImageView imageView;
        private TLRPC.TL_availableReaction reaction;
        private boolean imageSet = false;

        public ReactionCell(Context context) {
            super(context);

            imageView = new BackupImageView(context);
            imageView.setAspectFit(true);
            imageView.setLayerNum(1);

            this.addView(imageView, LayoutHelper.createLinear(36, 36, Gravity.CENTER, 5, 7, 5, 7));
        }

        public void setReaction(final TLRPC.TL_availableReaction r) {
            reaction = r;

//            imageView.getImageReceiver().setAutoRepeat(0);

            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(reaction.select_animation, Theme.key_windowBackgroundGray, 1.0f);
            imageView.setImage(ImageLocation.getForDocument(reaction.select_animation), "50_50", "tgs", svgThumb, reaction);

            imageView.getImageReceiver().startAnimation();

            imageSet = true;
        }
    }
}
