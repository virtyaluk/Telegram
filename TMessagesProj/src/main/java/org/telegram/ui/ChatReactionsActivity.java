package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ReactionCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

public class ChatReactionsActivity extends BaseFragment {
    private final TLRPC.Chat currentChat;
    private final TLRPC.ChatFull info;
    private final long chatId;
    private final boolean isChannel;
    private boolean enableReactions;
    private HashSet<String> enabledReactions;
    private final ArrayList<String> availableReactions;

    private ListAdapter listViewAdapter;
    private LinearLayoutManager layoutManager;
    private RecyclerListView reactionsList;

    private int rowCount = 0;
    private final int enableReactionsCellRow;
    private final int reactionsHintCellRow;
    private final int reactionsListHeaderRow;

    public ChatReactionsActivity(Bundle args, TLRPC.ChatFull chatFull) {
        super(args);

        info = chatFull;
        chatId = arguments.getLong("chat_id");
        currentChat = getMessagesController().getChat(chatId);
        isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;

        enableReactionsCellRow = rowCount++;
        reactionsHintCellRow = rowCount++;
        reactionsListHeaderRow = rowCount++;

        if (info != null && info.available_reactions != null) {
            enabledReactions = new HashSet<>(info.available_reactions);
        } else {
            enabledReactions = new HashSet<>();
        }

        availableReactions = new ArrayList<>();

        for (Map.Entry<String, TLRPC.TL_availableReaction> kvp: getMessagesController().availableReactions.entrySet()) {
            availableReactions.add(kvp.getValue().reaction);
            rowCount++;
        }

        enableReactions = info != null && info.available_reactions != null && !info.available_reactions.isEmpty();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("Reactions", R.string.Reactions));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    processDone();
                }
            }
        });

        fragmentView = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray));
                super.dispatchDraw(canvas);
            }
        };

        FrameLayout frameLayout = (FrameLayout) fragmentView;

        reactionsList = new RecyclerListView(context) {
            @Override
            public void invalidate() {
                super.invalidate();
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            }
        };

        reactionsList.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        reactionsList.setAdapter(listViewAdapter = new ChatReactionsActivity.ListAdapter(context));
        reactionsList.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        reactionsList.setOnItemClickListener((view, pos, x, y) -> {
            if (view instanceof ReactionCheckCell) {
                ReactionCheckCell reactionCheckCell = (ReactionCheckCell) view;

                reactionCheckCell.setChecked(!reactionCheckCell.isChecked());

                if (reactionCheckCell.isChecked()) {
                    enabledReactions.add(reactionCheckCell.getReaction().reaction);
                } else {
                    enabledReactions.remove(reactionCheckCell.getReaction().reaction);
                }
            } else if (view instanceof TextCheckCell) {
                TextCheckCell textCheckCell = (TextCheckCell) view;

                enableReactions = !enableReactions;
                textCheckCell.setChecked(!textCheckCell.isChecked());
                updateTextCheckCellColors(textCheckCell, enableReactions);

                if (enableReactions) {
                    enabledReactions = new HashSet<>(availableReactions);
                }

//                listViewAdapter.notifyItemChanged(pos + 1);
                listViewAdapter.notifyDataSetChanged();
            }
        });

        frameLayout.addView(reactionsList, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private static void updateTextCheckCellColors(TextCheckCell textCheckCell, boolean isChecked) {
        if (isChecked) {
            textCheckCell.setBackgroundColor(Theme.getColor(Theme.key_switchTrackChecked));
            textCheckCell.setColors(
                    Theme.key_actionBarDefaultIcon,
                    Theme.key_switchTrack,
                    Theme.key_actionBarDefault,
                    Theme.key_windowBackgroundWhite,
                    Theme.key_windowBackgroundWhite
            );
        } else {
            textCheckCell.setBackgroundColor(Theme.getColor(Theme.key_dialogTextGray3));
            textCheckCell.setColors(
                    Theme.key_actionBarDefaultIcon, // key
                    Theme.key_dialogTextGray2, // switchKey
                    Theme.key_dialogTextGray2, // switchKeyChecked
                    Theme.key_windowBackgroundWhite, // switchThumb
                    Theme.key_windowBackgroundWhite // switchThumbChecked
            );
        }
    }

    private void processDone() {
        HashSet<String> prevAvailableReactions;

        if (info != null && info.available_reactions != null) {
            prevAvailableReactions = new HashSet<>(info.available_reactions);
        } else {
            prevAvailableReactions = new HashSet<>();
        }

        if (!prevAvailableReactions.equals(enabledReactions)) {
            TLRPC.TL_messages_setChatAvailableReactions req = new TLRPC.TL_messages_setChatAvailableReactions();
            req.peer = getMessagesController().getInputPeer(currentChat);
            req.available_reactions = new ArrayList<>(enabledReactions);

            getConnectionsManager().sendRequest(req, ((response, error) -> {
                if (error != null) {
                    return;
                }

                AndroidUtilities.runOnUIThread(() -> {
                    info.available_reactions = req.available_reactions;
                    getMessagesStorage().updateChatInfo(info, false);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, false, false);
                });
            }), ConnectionsManager.RequestFlagInvokeAfter);
        }

        finishFragment();
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(reactionsList, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextCheckCell.class, TextDetailSettingsCell.class, TextSettingsCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(reactionsList, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(reactionsList, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(reactionsList, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(reactionsList, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(reactionsList, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(reactionsList, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(reactionsList, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(reactionsList, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(reactionsList, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(reactionsList, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(reactionsList, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(reactionsList, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(reactionsList, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(reactionsList, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(reactionsList, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(reactionsList, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(reactionsList, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        themeDescriptions.add(new ThemeDescription(reactionsList, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(reactionsList, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(reactionsList, ThemeDescription.FLAG_LINKCOLOR, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));

        return themeDescriptions;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();

            return position != reactionsHintCellRow && position != reactionsListHeaderRow;
        }

        @Override
        public int getItemCount() {
            return enableReactions ? rowCount : 2;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;

            if (viewType == 0) {
                view = new ReactionCheckCell(mContext);
            } else if (viewType == 1) {
                view = new TextCheckCell(mContext);
            } else if (viewType == 2) {
                view = new TextInfoPrivacyCell(mContext);
            } else if (viewType == 3) {
                view = new HeaderCell(mContext);
            }

            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof HeaderCell) {
                HeaderCell headerCell = (HeaderCell) holder.itemView;

                headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                headerCell.setHeight(50);
                headerCell.setText(LocaleController.getString("ReactionsListTitle", R.string.ReactionsListTitle));
            } else if (holder.itemView instanceof TextCheckCell) {
                TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;

                textCheckCell.setHeight(60);
                textCheckCell.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                textCheckCell.setTextAndCheck(LocaleController.getString("ReactionsEnable", R.string.ReactionsEnable), enableReactions, false);

                updateTextCheckCellColors(textCheckCell, enableReactions);
            } else if (holder.itemView instanceof TextInfoPrivacyCell) {
                TextInfoPrivacyCell reactionsHelpText = (TextInfoPrivacyCell) holder.itemView;

                reactionsHelpText.setText(LocaleController.getString(isChannel ? "ReactionsHintChannel": "ReactionsHintGroup", isChannel ? R.string.ReactionsHintChannel : R.string.ReactionsHintGroup));
            } else {
                ReactionCheckCell reactionCheckCell = (ReactionCheckCell) holder.itemView;

                TLRPC.TL_availableReaction reaction = getMessagesController().availableReactions.get(availableReactions.get(position - 3));

                reactionCheckCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                reactionCheckCell.setReaction(reaction, enabledReactions.contains(reaction.reaction), true);
                reactionCheckCell.setHeight(60);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == enableReactionsCellRow ) {
                return 1;
            } else if (position == reactionsHintCellRow) {
                return 2;
            } else if (position == reactionsListHeaderRow) {
                return 3;
            }

            return 0;
        }
    }
}
