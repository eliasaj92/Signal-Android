/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.DimenRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.components.AlertView;
import org.thoughtcrime.securesms.components.AudioView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.DeliveryStatusView;
import org.thoughtcrime.securesms.components.DocumentView;
import org.thoughtcrime.securesms.components.ExpirationTimerView;
import org.thoughtcrime.securesms.components.QuoteView;
import org.thoughtcrime.securesms.components.SharedContactView;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.Quote;
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob;
import org.thoughtcrime.securesms.jobs.MmsDownloadJob;
import org.thoughtcrime.securesms.jobs.MmsSendJob;
import org.thoughtcrime.securesms.jobs.SmsSendJob;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideClickListener;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.LongClickCopySpan;
import org.thoughtcrime.securesms.util.LongClickMovementMethod;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.dualsim.SubscriptionInfoCompat;
import org.thoughtcrime.securesms.util.dualsim.SubscriptionManagerCompat;
import org.thoughtcrime.securesms.util.views.Stub;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A view that displays an individual conversation item within a conversation
 * thread.  Used by ComposeMessageActivity's ListActivity via a ConversationAdapter.
 *
 * @author Moxie Marlinspike
 *
 */

public class ConversationItem extends LinearLayout
    implements RecipientModifiedListener, BindableConversationItem
{
  private final static String TAG = ConversationItem.class.getSimpleName();

  private MessageRecord messageRecord;
  private Locale        locale;
  private boolean       groupThread;
  private Recipient     recipient;
  private GlideRequests glideRequests;

  protected View             bodyBubble;
  private QuoteView          quoteView;
  private TextView           bodyText;
  private ViewGroup          footer;
  private TextView           dateText;
  private TextView           simInfoText;
  private TextView           indicatorText;
  private TextView           groupSender;
  private TextView           groupSenderProfileName;
  private View               groupSenderHolder;
  private ImageView          insecureImage;
  private AvatarImageView    contactPhoto;
  private DeliveryStatusView deliveryStatusIndicator;
  private AlertView          alertView;
  private ViewGroup          container;

  private @NonNull  Set<MessageRecord>      batchSelected = new HashSet<>();
  private @NonNull  Recipient               conversationRecipient;
  private @NonNull  Stub<ThumbnailView>     mediaThumbnailStub;
  private @NonNull  Stub<AudioView>         audioViewStub;
  private @NonNull  Stub<DocumentView>      documentViewStub;
  private @NonNull  Stub<SharedContactView> sharedContactStub;
  private @NonNull  ExpirationTimerView     expirationTimer;
  private @Nullable EventListener           eventListener;

  private int defaultBubbleColor;

  private final PassthroughClickListener        passthroughClickListener   = new PassthroughClickListener();
  private final AttachmentDownloadClickListener downloadClickListener      = new AttachmentDownloadClickListener();
  private final SharedContactEventListener      sharedContactEventListener = new SharedContactEventListener();
  private final SharedContactClickListener      sharedContactClickListener = new SharedContactClickListener();

  private final Context context;

  public ConversationItem(Context context) {
    this(context, null);
  }

  public ConversationItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  @Override
  public void setOnClickListener(OnClickListener l) {
    super.setOnClickListener(new ClickListener(l));
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    initializeAttributes();

    this.bodyText                =            findViewById(R.id.conversation_item_body);
    this.footer                  =            findViewById(R.id.conversation_item_footer);
    this.dateText                =            findViewById(R.id.conversation_item_date);
    this.simInfoText             =            findViewById(R.id.sim_info);
    this.indicatorText           =            findViewById(R.id.indicator_text);
    this.groupSender             =            findViewById(R.id.group_message_sender);
    this.groupSenderProfileName  =            findViewById(R.id.group_message_sender_profile);
    this.insecureImage           =            findViewById(R.id.insecure_indicator);
    this.deliveryStatusIndicator =            findViewById(R.id.delivery_status);
    this.alertView               =            findViewById(R.id.indicators_parent);
    this.contactPhoto            =            findViewById(R.id.contact_photo);
    this.bodyBubble              =            findViewById(R.id.body_bubble);
    this.mediaThumbnailStub      = new Stub<>(findViewById(R.id.image_view_stub));
    this.audioViewStub           = new Stub<>(findViewById(R.id.audio_view_stub));
    this.documentViewStub        = new Stub<>(findViewById(R.id.document_view_stub));
    this.sharedContactStub       = new Stub<>(findViewById(R.id.shared_contact_view_stub));
    this.expirationTimer         =            findViewById(R.id.expiration_indicator);
    this.groupSenderHolder       =            findViewById(R.id.group_sender_holder);
    this.quoteView               =            findViewById(R.id.quote_view);
    this.container               =            findViewById(R.id.container);

    setOnClickListener(new ClickListener(null));

    bodyText.setOnLongClickListener(passthroughClickListener);
    bodyText.setOnClickListener(passthroughClickListener);

    bodyText.setMovementMethod(LongClickMovementMethod.getInstance(getContext()));
  }

  @Override
  public void bind(@NonNull MessageRecord           messageRecord,
                   @NonNull Optional<MessageRecord> previousMessageRecord,
                   @NonNull Optional<MessageRecord> nextMessageRecord,
                   @NonNull GlideRequests           glideRequests,
                   @NonNull Locale                  locale,
                   @NonNull Set<MessageRecord>      batchSelected,
                   @NonNull Recipient               conversationRecipient,
                            boolean                 pulseHighlight)
  {
    this.messageRecord          = messageRecord;
    this.locale                 = locale;
    this.glideRequests          = glideRequests;
    this.batchSelected          = batchSelected;
    this.conversationRecipient  = conversationRecipient;
    this.groupThread            = conversationRecipient.isGroupRecipient();
    this.recipient              = messageRecord.getIndividualRecipient();

    this.recipient.addListener(this);
    this.conversationRecipient.addListener(this);

    updateLayoutParams(footer, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    dateText.forceLayout();

    setMessageBackground(messageRecord, previousMessageRecord, nextMessageRecord, groupThread);
    setMediaAttributes(messageRecord, previousMessageRecord, nextMessageRecord, conversationRecipient, groupThread);
    setInteractionState(messageRecord, pulseHighlight);
    setBodyText(messageRecord);
    setBubbleState(messageRecord, conversationRecipient);
    setStatusIcons(messageRecord);
    setContactPhoto(recipient);
    setGroupMessageStatus(messageRecord, recipient);
    setMinimumWidth();
    setSimInfo(messageRecord);
    setExpiration(messageRecord);
    setQuote(messageRecord);
    setMessageSpacing(messageRecord, nextMessageRecord);
    setMessageMargins(messageRecord, groupThread);
    setAuthorTitleVisibility(messageRecord, previousMessageRecord, groupThread);
    setAuthorAvatarVisibility(messageRecord, nextMessageRecord, groupThread);
  }

  @Override
  public void setEventListener(@Nullable EventListener eventListener) {
    this.eventListener = eventListener;
  }

  @Override
  public void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);

    if (groupSenderHolder != null && groupSenderHolder.getVisibility() == View.VISIBLE) {
      View content = (View) groupSenderHolder.getParent();

       groupSenderHolder.layout(content.getPaddingLeft() + getLeftMargin(groupSenderHolder),
                                content.getPaddingTop(),
                                content.getWidth() - content.getPaddingRight() - getRightMargin(groupSenderHolder),
                                content.getPaddingTop() + groupSenderHolder.getMeasuredHeight());


      if (ViewCompat.getLayoutDirection(groupSenderProfileName) == ViewCompat.LAYOUT_DIRECTION_RTL) {
        groupSenderProfileName.layout(groupSenderHolder.getPaddingLeft(),
                                      groupSenderHolder.getPaddingTop(),
                                      groupSenderHolder.getPaddingLeft() + groupSenderProfileName.getWidth(),
                                      groupSenderHolder.getPaddingTop() + groupSenderProfileName.getHeight());
      } else {
        groupSenderProfileName.layout(groupSenderHolder.getWidth() - groupSenderHolder.getPaddingRight() - groupSenderProfileName.getWidth(),
                                      groupSenderHolder.getPaddingTop(),
                                      groupSenderHolder.getWidth() - groupSenderProfileName.getPaddingRight(),
                                      groupSenderHolder.getPaddingTop() + groupSenderProfileName.getHeight());
      }
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    if (isInEditMode()) {
      return;
    }

    boolean needsMeasure = false;

    if (hasQuote(messageRecord)) {
      int quoteWidth     = quoteView.getMeasuredWidth();
      int availableWidth = getAvailableMessageBubbleWidth(quoteView);

      if (quoteWidth != availableWidth) {
        quoteView.getLayoutParams().width = availableWidth;
        needsMeasure = true;
      }
    }

    int availableWidth = getAvailableMessageBubbleWidth(footer);
    if (footer.getMeasuredWidth() != availableWidth) {
      footer.getLayoutParams().width = availableWidth;
      needsMeasure = true;
    }

    if (needsMeasure) {
      measure(widthMeasureSpec, heightMeasureSpec);
    }
  }

  private int getAvailableMessageBubbleWidth(@NonNull View forView) {
    int availableWidth;
    if (hasAudio(messageRecord)) {
      availableWidth = audioViewStub.get().getMeasuredWidth() + getLeftMargin(audioViewStub.get()) + getRightMargin(audioViewStub.get());
    } else if (hasThumbnail(messageRecord)) {
      availableWidth = mediaThumbnailStub.get().getMeasuredWidth();
    } else {
      availableWidth = bodyBubble.getMeasuredWidth() - bodyBubble.getPaddingLeft() - bodyBubble.getPaddingRight();
    }

    availableWidth -= getLeftMargin(forView) + getRightMargin(forView);

    return availableWidth;
  }

  private void initializeAttributes() {
    final int[]      attributes = new int[] {R.attr.conversation_item_bubble_background};
    final TypedArray attrs      = context.obtainStyledAttributes(attributes);

    defaultBubbleColor = attrs.getColor(0, Color.WHITE);
    attrs.recycle();
  }

  @Override
  public void unbind() {
    if (recipient != null) {
      recipient.removeListener(this);
    }

    this.expirationTimer.stopAnimation();
  }

  public MessageRecord getMessageRecord() {
    return messageRecord;
  }

  /// MessageRecord Attribute Parsers

  private void setBubbleState(MessageRecord messageRecord, Recipient conversationRecipient) {
    if (messageRecord.isOutgoing()) {
      bodyBubble.getBackground().setColorFilter(defaultBubbleColor, PorterDuff.Mode.MULTIPLY);
    } else {
      bodyBubble.getBackground().setColorFilter(defaultBubbleColor, PorterDuff.Mode.MULTIPLY);
      bodyBubble.getBackground().setColorFilter(messageRecord.getRecipient().getColor().toConversationColor(context), PorterDuff.Mode.MULTIPLY);
    }

    if (audioViewStub.resolved()) {
      setAudioViewTint(messageRecord, this.conversationRecipient);
    }
  }

  private void setAudioViewTint(MessageRecord messageRecord, Recipient recipient) {
    if (messageRecord.isOutgoing()) {
      if (DynamicTheme.LIGHT.equals(TextSecurePreferences.getTheme(context))) {
        audioViewStub.get().setTint(getContext().getResources().getColor(R.color.core_light_60), defaultBubbleColor);
      } else {
        audioViewStub.get().setTint(Color.WHITE, defaultBubbleColor);
      }
    } else {
      audioViewStub.get().setTint(Color.WHITE, recipient.getColor().toConversationColor(context));
    }
  }

  private void setInteractionState(MessageRecord messageRecord, boolean pulseHighlight) {
    if (batchSelected.contains(messageRecord)) {
      setBackgroundResource(R.drawable.conversation_item_background);
      setSelected(true);
    } else if (pulseHighlight) {
      setBackgroundResource(R.drawable.conversation_item_background_animated);
      setSelected(true);
      postDelayed(() -> setSelected(false), 500);
    } else {
      setSelected(false);
    }

    if (mediaThumbnailStub.resolved()) {
      mediaThumbnailStub.get().setFocusable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
      mediaThumbnailStub.get().setClickable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
      mediaThumbnailStub.get().setLongClickable(batchSelected.isEmpty());
    }

    if (audioViewStub.resolved()) {
      audioViewStub.get().setFocusable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
      audioViewStub.get().setClickable(batchSelected.isEmpty());
      audioViewStub.get().setEnabled(batchSelected.isEmpty());
    }

    if (documentViewStub.resolved()) {
      documentViewStub.get().setFocusable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
      documentViewStub.get().setClickable(batchSelected.isEmpty());
    }
  }

  private boolean isCaptionlessMms(MessageRecord messageRecord) {
    return TextUtils.isEmpty(messageRecord.getDisplayBody()) && messageRecord.isMms();
  }

  private boolean hasAudio(MessageRecord messageRecord) {
    return messageRecord.isMms() && ((MmsMessageRecord)messageRecord).getSlideDeck().getAudioSlide() != null;
  }

  private boolean hasThumbnail(MessageRecord messageRecord) {
    return messageRecord.isMms() && ((MmsMessageRecord)messageRecord).getSlideDeck().getThumbnailSlide() != null;
  }

  private boolean hasDocument(MessageRecord messageRecord) {
    return messageRecord.isMms() && ((MmsMessageRecord)messageRecord).getSlideDeck().getDocumentSlide() != null;
  }

  private boolean hasQuote(MessageRecord messageRecord) {
    return messageRecord.isMms() && ((MmsMessageRecord)messageRecord).getQuote() != null;
  }

  private boolean hasSharedContact(MessageRecord messageRecord) {
    return messageRecord.isMms() && !((MmsMessageRecord)messageRecord).getSharedContacts().isEmpty();
  }

  private void setBodyText(MessageRecord messageRecord) {
    bodyText.setClickable(false);
    bodyText.setFocusable(false);
    bodyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, TextSecurePreferences.getMessageBodyTextSize(context));

    if (isCaptionlessMms(messageRecord)) {
      bodyText.setVisibility(View.GONE);
    } else {
      bodyText.setText(linkifyMessageBody(messageRecord.getDisplayBody(), batchSelected.isEmpty()));
      bodyText.setVisibility(View.VISIBLE);
    }
  }

  private void setMediaAttributes(@NonNull MessageRecord           currentMessage,
                                  @NonNull Optional<MessageRecord> previousMessage,
                                  @NonNull Optional<MessageRecord> nextMessage,
                                  @NonNull Recipient               conversationRecipient,
                                           boolean                 isGroupThread)
  {
    boolean showControls = !currentMessage.isFailed() && !Util.isOwnNumber(context, conversationRecipient.getAddress());

    updateLayoutParams(bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    setPaddingTop(bodyBubble, readDimen(R.dimen.message_bubble_top_padding));
    setPaddingBottom(bodyBubble, 0);
    setTopMargin(footer, readDimen(R.dimen.message_bubble_footer_top_padding));
    setBottomMargin(footer, readDimen(R.dimen.message_bubble_bottom_padding));
    dateText.setTextAppearance(context, currentMessage.isOutgoing() ? R.style.Signal_Text_Caption_MessageSent : R.style.Signal_Text_Caption_MessageReceived);
    deliveryStatusIndicator.setTint(ThemeUtil.getThemedColor(context, R.attr.conversation_item_sent_text_secondary_color));

    if (hasSharedContact(currentMessage)) {
      sharedContactStub.get().setVisibility(VISIBLE);
      if (audioViewStub.resolved())      mediaThumbnailStub.get().setVisibility(View.GONE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved())   documentViewStub.get().setVisibility(View.GONE);

      sharedContactStub.get().setContact(((MediaMmsMessageRecord) currentMessage).getSharedContacts().get(0), glideRequests, locale);
      sharedContactStub.get().setEventListener(sharedContactEventListener);
      sharedContactStub.get().setOnClickListener(sharedContactClickListener);
      sharedContactStub.get().setOnLongClickListener(passthroughClickListener);

      setTopMargin(footer, getSharedContactFooterDisplacement());
      setBottomMargin(footer, 0);
      setPaddingBottom(bodyBubble, readDimen(R.dimen.message_bubble_bottom_padding_shared_contact_displacement));
    } else if (hasAudio(currentMessage)) {
      audioViewStub.get().setVisibility(View.VISIBLE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved())   documentViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved())  sharedContactStub.get().setVisibility(GONE);

      //noinspection ConstantConditions
      audioViewStub.get().setAudio(((MediaMmsMessageRecord) currentMessage).getSlideDeck().getAudioSlide(), showControls);
      audioViewStub.get().setDownloadClickListener(downloadClickListener);
      audioViewStub.get().setOnLongClickListener(passthroughClickListener);
    } else if (hasDocument(currentMessage)) {
      documentViewStub.get().setVisibility(View.VISIBLE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (audioViewStub.resolved())      audioViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved())  sharedContactStub.get().setVisibility(GONE);

      //noinspection ConstantConditions
      documentViewStub.get().setDocument(((MediaMmsMessageRecord)currentMessage).getSlideDeck().getDocumentSlide(), showControls);
      documentViewStub.get().setDocumentClickListener(new ThumbnailClickListener());
      documentViewStub.get().setDownloadClickListener(downloadClickListener);
      documentViewStub.get().setOnLongClickListener(passthroughClickListener);
    } else if (hasThumbnail(currentMessage)) {
      mediaThumbnailStub.get().setVisibility(View.VISIBLE);
      if (audioViewStub.resolved())    audioViewStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved())  sharedContactStub.get().setVisibility(GONE);

      setThumbnailCorners(currentMessage, previousMessage, nextMessage, isGroupThread);

      //noinspection ConstantConditions
      Slide      thumbnailSlide = ((MmsMessageRecord) currentMessage).getSlideDeck().getThumbnailSlide();
      Attachment attachment     = thumbnailSlide.asAttachment();
      mediaThumbnailStub.get().setImageResource(glideRequests,
                                                thumbnailSlide,
                                                showControls,
                                                false,
                                                attachment.getWidth(),
                                                attachment.getHeight());
      mediaThumbnailStub.get().setThumbnailClickListener(new ThumbnailClickListener());
      mediaThumbnailStub.get().setDownloadClickListener(downloadClickListener);
      mediaThumbnailStub.get().setOnLongClickListener(passthroughClickListener);
      mediaThumbnailStub.get().setOnClickListener(passthroughClickListener);
      mediaThumbnailStub.get().showShade(true);

      updateLayoutParams(bodyText, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

      if (!hasQuote(currentMessage)) {
        setPaddingTop(bodyBubble, 0);
      } else {
        setPaddingTop(bodyBubble, readDimen(R.dimen.message_bubble_top_padding));
      }

      if (TextUtils.isEmpty(currentMessage.getDisplayBody())) {
        setTopMargin(footer, getImageFooterDisplacement());
        setBottomMargin(footer, 0);
        dateText.setTextAppearance(context, R.style.Signal_Text_Caption_MessageImageOverlay);
        deliveryStatusIndicator.setTint(getContext().getResources().getColor(R.color.core_white));
        mediaThumbnailStub.get().setBackgroundResource(getCornerBackgroundRes(currentMessage, previousMessage, nextMessage, isGroupThread));

        if (!isGroupThread || !isStartOfMessageCluster(currentMessage, previousMessage, isGroupThread)) {
          bodyBubble.setBackgroundColor(Color.TRANSPARENT);
        }
      }
    } else {
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (audioViewStub.resolved())      audioViewStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved())   documentViewStub.get().setVisibility(View.GONE);
      if (sharedContactStub.resolved())  sharedContactStub.get().setVisibility(GONE);
    }
  }

  private void setThumbnailCorners(@NonNull MessageRecord           current,
                                   @NonNull Optional<MessageRecord> previous,
                                   @NonNull Optional<MessageRecord> next,
                                            boolean                 isGroupThread)
  {
    int defaultRadius  = readDimen(R.dimen.message_corner_radius);
    int collapseRadius = readDimen(R.dimen.message_corner_collapse_radius);

    int topLeft     = defaultRadius;
    int topRight    = defaultRadius;
    int bottomLeft  = defaultRadius;
    int bottomRight = defaultRadius;

    if (isStartOfMessageCluster(current, previous, isGroupThread) && isEndOfMessageCluster(current, next, isGroupThread)) {
      topLeft     = defaultRadius;
      topRight    = defaultRadius;
      bottomLeft  = defaultRadius;
      bottomRight = defaultRadius;
    } else if (isStartOfMessageCluster(current, previous, isGroupThread)) {
      if (current.isOutgoing()) {
        bottomRight = collapseRadius;
      } else {
        bottomLeft = collapseRadius;
      }
    } else if (isEndOfMessageCluster(current, next, isGroupThread)) {
      if (current.isOutgoing()) {
        topRight = collapseRadius;
      } else {
        topLeft = collapseRadius;
      }
    } else {
      if (current.isOutgoing()) {
        topRight    = collapseRadius;
        bottomRight = collapseRadius;
      } else {
        topLeft    = collapseRadius;
        bottomLeft = collapseRadius;
      }
    }

    if (!TextUtils.isEmpty(current.getDisplayBody())) {
      bottomLeft  = 0;
      bottomRight = 0;
    }

    if (isStartOfMessageCluster(current, previous, isGroupThread) && !current.isOutgoing() && isGroupThread) {
      topLeft  = 0;
      topRight = 0;
    }

    mediaThumbnailStub.get().setCornerRadii(topLeft, topRight, bottomRight, bottomLeft);
  }

  private void updateLayoutParams(@NonNull View view, int width, int height) {
    view.getLayoutParams().width  = width;
    view.getLayoutParams().height = height;
    view.requestLayout();
  }

  private int getLeftMargin(@NonNull View view) {
    if (ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_LTR) {
      return ((MarginLayoutParams) view.getLayoutParams()).leftMargin;
    }
    return ((MarginLayoutParams) view.getLayoutParams()).rightMargin;
  }

  private int getRightMargin(@NonNull View view) {
    if (ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_LTR) {
      return ((MarginLayoutParams) view.getLayoutParams()).rightMargin;
    }
    return ((MarginLayoutParams) view.getLayoutParams()).leftMargin;
  }

  private void setLeftMargin(@NonNull View view, int margin) {
    if (ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_LTR) {
      ((MarginLayoutParams) view.getLayoutParams()).leftMargin = margin;
    } else {
      ((MarginLayoutParams) view.getLayoutParams()).rightMargin = margin;
    }
    view.requestLayout();
  }

  private void setTopMargin(@NonNull View view, int margin) {
    ((MarginLayoutParams) view.getLayoutParams()).topMargin = margin;
    view.requestLayout();
  }

  private void setBottomMargin(@NonNull View view, int margin) {
    ((MarginLayoutParams) view.getLayoutParams()).bottomMargin = margin;
    view.requestLayout();
  }

  private void setPaddingTop(@NonNull View view, int padding) {
    view.setPadding(view.getPaddingLeft(), padding, view.getPaddingRight(), view.getPaddingBottom());
  }

  private void setPaddingBottom(@NonNull View view, int padding) {
    view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), padding);
  }

  private void setContactPhoto(@NonNull Recipient recipient) {
    if (contactPhoto == null) return;

    if (messageRecord.isOutgoing() || !groupThread) {
      contactPhoto.setVisibility(View.GONE);
    } else {
      contactPhoto.setAvatar(glideRequests, recipient, true);
      contactPhoto.setVisibility(View.VISIBLE);
    }
  }

  private SpannableString linkifyMessageBody(SpannableString messageBody, boolean shouldLinkifyAllLinks) {
    boolean hasLinks = Linkify.addLinks(messageBody, shouldLinkifyAllLinks ? Linkify.ALL : 0);

    if (hasLinks) {
      URLSpan[] urlSpans = messageBody.getSpans(0, messageBody.length(), URLSpan.class);
      for (URLSpan urlSpan : urlSpans) {
        int start = messageBody.getSpanStart(urlSpan);
        int end = messageBody.getSpanEnd(urlSpan);
        messageBody.setSpan(new LongClickCopySpan(urlSpan.getURL()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    return messageBody;
  }

  private void setStatusIcons(MessageRecord messageRecord) {
    indicatorText.setVisibility(View.GONE);

    insecureImage.setVisibility(messageRecord.isSecure() ? View.GONE : View.VISIBLE);
    bodyText.setCompoundDrawablesWithIntrinsicBounds(0, 0, messageRecord.isKeyExchange() ? R.drawable.ic_menu_login : 0, 0);
    dateText.setText(DateUtils.getExtendedRelativeTimeSpanString(getContext(), locale, messageRecord.getTimestamp()));

    if (messageRecord.isFailed()) {
      setFailedStatusIcons();
    } else if (messageRecord.isPendingInsecureSmsFallback()) {
      setFallbackStatusIcons();
    } else {
      alertView.setNone();

      if      (!messageRecord.isOutgoing())  deliveryStatusIndicator.setNone();
      else if (messageRecord.isPending())    deliveryStatusIndicator.setPending();
      else if (messageRecord.isRemoteRead()) deliveryStatusIndicator.setRead();
      else if (messageRecord.isDelivered())  deliveryStatusIndicator.setDelivered();
      else                                   deliveryStatusIndicator.setSent();
    }
  }

  private void setSimInfo(MessageRecord messageRecord) {
    SubscriptionManagerCompat subscriptionManager = new SubscriptionManagerCompat(context);

    if (messageRecord.getSubscriptionId() == -1 || !Permissions.hasAll(context, Manifest.permission.READ_PHONE_STATE) || subscriptionManager.getActiveSubscriptionInfoList().size() < 2) {
      simInfoText.setVisibility(View.GONE);
    } else {
      Optional<SubscriptionInfoCompat> subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(messageRecord.getSubscriptionId());

      if (subscriptionInfo.isPresent() && messageRecord.isOutgoing()) {
        simInfoText.setText(getContext().getString(R.string.ConversationItem_from_s, subscriptionInfo.get().getDisplayName()));
        simInfoText.setVisibility(View.VISIBLE);
      } else if (subscriptionInfo.isPresent()) {
        simInfoText.setText(getContext().getString(R.string.ConversationItem_to_s,  subscriptionInfo.get().getDisplayName()));
        simInfoText.setVisibility(View.VISIBLE);
      } else {
        simInfoText.setVisibility(View.GONE);
      }
    }
  }

  @SuppressLint("StaticFieldLeak")
  private void setExpiration(final MessageRecord messageRecord) {
    if (messageRecord.getExpiresIn() > 0) {
      this.expirationTimer.setVisibility(View.VISIBLE);
      this.expirationTimer.setPercentage(0);

      if (messageRecord.getExpireStarted() > 0) {
        this.expirationTimer.setExpirationTime(messageRecord.getExpireStarted(),
                                               messageRecord.getExpiresIn());
        this.expirationTimer.startAnimation();

        if (messageRecord.getExpireStarted() + messageRecord.getExpiresIn() <= System.currentTimeMillis()) {
          ApplicationContext.getInstance(context).getExpiringMessageManager().checkSchedule();
        }
      } else if (!messageRecord.isOutgoing() && !messageRecord.isMediaPending()) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
            long                   id                = messageRecord.getId();
            boolean                mms               = messageRecord.isMms();

            if (mms) DatabaseFactory.getMmsDatabase(context).markExpireStarted(id);
            else     DatabaseFactory.getSmsDatabase(context).markExpireStarted(id);

            expirationManager.scheduleDeletion(id, mms, messageRecord.getExpiresIn());
            return null;
          }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
      }
    } else {
      this.expirationTimer.setVisibility(View.GONE);
    }
  }

  private void setQuote(@NonNull MessageRecord messageRecord) {
    if (messageRecord.isMms() && !messageRecord.isMmsNotification() && ((MediaMmsMessageRecord)messageRecord).getQuote() != null) {
      Quote quote = ((MediaMmsMessageRecord)messageRecord).getQuote();
      assert quote != null;
      quoteView.setQuote(glideRequests, quote.getId(), Recipient.from(context, quote.getAuthor(), true), quote.getText(), quote.getAttachment());
      quoteView.setVisibility(View.VISIBLE);
      quoteView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;

      quoteView.setOnClickListener(view -> {
        if (eventListener != null && batchSelected.isEmpty()) {
          eventListener.onQuoteClicked((MmsMessageRecord) messageRecord);
        } else {
          passthroughClickListener.onClick(view);
        }
      });
      quoteView.setOnLongClickListener(passthroughClickListener);
      setPaddingTop(bodyBubble, 0);
    } else {
      quoteView.dismiss();
    }
  }

  private void setMessageSpacing(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> next) {
    if (next.isPresent()) {
      boolean recipientsMatch = current.getRecipient().getAddress().equals(next.get().getRecipient().getAddress());
      boolean outgoingMatch   = current.isOutgoing() == next.get().isOutgoing();

      if (!recipientsMatch || !outgoingMatch) {
        setPaddingBottom(this, readDimen(R.dimen.conversation_vertical_message_spacing_default));
        return;
      }
    }
    setPaddingBottom(this, readDimen(R.dimen.conversation_vertical_message_spacing_collapse));
  }

  private void setMessageMargins(@NonNull MessageRecord message, boolean isGroupThread) {
    if (isGroupThread) {
      if (message.isOutgoing()) {
        setLeftMargin(container, readDimen(R.dimen.conversation_group_left_gutter));
      } else {
        setLeftMargin(bodyBubble, readDimen(R.dimen.conversation_group_left_gutter));
      }
    } else {
      if (message.isOutgoing()) {
        setLeftMargin(container, readDimen(R.dimen.conversation_individual_left_gutter));
      } else {
        setLeftMargin(bodyBubble, readDimen(R.dimen.conversation_individual_left_gutter));
      }
    }
  }

  private void setMessageBackground(@NonNull MessageRecord           current,
                                    @NonNull Optional<MessageRecord> previous,
                                    @NonNull Optional<MessageRecord> next,
                                             boolean                 isGroupThread)
  {
    bodyBubble.setBackgroundResource(getCornerBackgroundRes(current, previous, next, isGroupThread));
  }

  private void setAuthorTitleVisibility(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, boolean isGroupThread) {
    if (isGroupThread && !current.isOutgoing()) {
      if (!previous.isPresent() || previous.get().isUpdate() || !current.getRecipient().getAddress().equals(previous.get().getRecipient().getAddress())) {
        groupSenderHolder.setVisibility(VISIBLE);
        setPaddingTop(bodyBubble, readDimen(R.dimen.message_bubble_top_padding));
      } else {
        groupSenderHolder.setVisibility(GONE);
      }
    } else {
      groupSenderHolder.setVisibility(GONE);
    }
  }

  private void setAuthorAvatarVisibility(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    if (isGroupThread && !current.isOutgoing()) {
      if (!next.isPresent() || next.get().isUpdate() || !current.getRecipient().getAddress().equals(next.get().getRecipient().getAddress())) {
        contactPhoto.setVisibility(VISIBLE);
      } else {
        contactPhoto.setVisibility(GONE);
      }
    } else if (contactPhoto != null) {
      contactPhoto.setVisibility(GONE);
    }
  }

  private boolean isStartOfMessageCluster(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, boolean isGroupThread) {
    if (isGroupThread) {
      return !previous.isPresent() || previous.get().isUpdate() || !current.getRecipient().getAddress().equals(previous.get().getRecipient().getAddress());
    } else {
      return !previous.isPresent() || previous.get().isUpdate() || current.isOutgoing() != previous.get().isOutgoing();
    }
  }

  private boolean isEndOfMessageCluster(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    if (isGroupThread) {
      return !next.isPresent() || next.get().isUpdate() || !current.getRecipient().getAddress().equals(next.get().getRecipient().getAddress());
    } else {
      return !next.isPresent() || next.get().isUpdate() || current.isOutgoing() != next.get().isOutgoing();
    }
  }

  private int getCornerBackgroundRes(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> previous, @NonNull Optional<MessageRecord> next, boolean isGroupThread) {
    if (isStartOfMessageCluster(current, previous, isGroupThread) && isEndOfMessageCluster(current, next, isGroupThread)) {
      return current.isOutgoing() ? R.drawable.message_bubble_background_sent_alone
                                  : R.drawable.message_bubble_background_received_alone;
    } else if (isStartOfMessageCluster(current, previous, isGroupThread)) {
      return current.isOutgoing() ? R.drawable.message_bubble_background_sent_start
                                  : R.drawable.message_bubble_background_received_start;
    } else if (isEndOfMessageCluster(current, next, isGroupThread)) {
      return current.isOutgoing() ? R.drawable.message_bubble_background_sent_end
                                  : R.drawable.message_bubble_background_received_end;
    } else {
      return current.isOutgoing() ? R.drawable.message_bubble_background_sent_middle
                                  : R.drawable.message_bubble_background_received_middle;
    }
  }

  private int readDimen(@DimenRes int dimenId) {
    return context.getResources().getDimensionPixelOffset(dimenId);
  }

  private int getImageFooterDisplacement() {
    return readDimen(R.dimen.message_bubble_footer_image_displacement_dp) +
           readDimen(R.dimen.message_bubble_footer_image_displacement_sp);
  }

  private int getSharedContactFooterDisplacement() {
    return readDimen(R.dimen.message_bubble_footer_shared_contact_displacement_dp) +
           readDimen(R.dimen.message_bubble_footer_shared_contact_displacement_sp);
  }

  private void setFailedStatusIcons() {
    alertView.setFailed();
    deliveryStatusIndicator.setNone();
    dateText.setText(R.string.ConversationItem_error_not_delivered);

    if (messageRecord.isOutgoing()) {
      indicatorText.setText(R.string.ConversationItem_click_for_details);
      indicatorText.setVisibility(View.VISIBLE);
    }
  }

  private void setFallbackStatusIcons() {
    alertView.setPendingApproval();
    deliveryStatusIndicator.setNone();
    indicatorText.setVisibility(View.VISIBLE);
    indicatorText.setText(R.string.ConversationItem_click_to_approve_unencrypted);
  }

  private void setMinimumWidth() {
    if (indicatorText.getVisibility() == View.VISIBLE && indicatorText.getText() != null) {
      final float density = getResources().getDisplayMetrics().density;
      bodyBubble.setMinimumWidth(indicatorText.getText().length() * (int) (6.5 * density) + (int) (22.0 * density));
    } else {
      bodyBubble.setMinimumWidth(0);
    }
  }

  private boolean shouldInterceptClicks(MessageRecord messageRecord) {
    return batchSelected.isEmpty() &&
            ((messageRecord.isFailed() && !messageRecord.isMmsNotification()) ||
            messageRecord.isPendingInsecureSmsFallback() ||
            messageRecord.isBundleKeyExchange());
  }

  @SuppressLint("SetTextI18n")
  private void setGroupMessageStatus(MessageRecord messageRecord, Recipient recipient) {
    this.groupSender.setText(recipient.toShortString());

    if (recipient.getName() == null && !TextUtils.isEmpty(recipient.getProfileName())) {
      this.groupSenderProfileName.setText("~" + recipient.getProfileName());
      this.groupSenderProfileName.setVisibility(View.VISIBLE);
    } else {
      this.groupSenderProfileName.setText(null);
      this.groupSenderProfileName.setVisibility(View.GONE);
    }
  }

  /// Event handlers

  private void handleApproveIdentity() {
    List<IdentityKeyMismatch> mismatches = messageRecord.getIdentityKeyMismatches();

    if (mismatches.size() != 1) {
      throw new AssertionError("Identity mismatch count: " + mismatches.size());
    }

    new ConfirmIdentityDialog(context, messageRecord, mismatches.get(0)).show();
  }

  @Override
  public void onModified(final Recipient modified) {
    Util.runOnMain(() -> {
      setBubbleState(messageRecord, conversationRecipient);
      setContactPhoto(recipient);
      setGroupMessageStatus(messageRecord, recipient);
      setAudioViewTint(messageRecord, conversationRecipient);
    });
  }

  private class SharedContactEventListener implements SharedContactView.EventListener {
    @Override
    public void onAddToContactsClicked(@NonNull Contact contact) {
      if (eventListener != null && batchSelected.isEmpty()) {
        eventListener.onAddToContactsClicked(contact);
      } else {
        passthroughClickListener.onClick(sharedContactStub.get());
      }
    }

    @Override
    public void onInviteClicked(@NonNull List<Recipient> choices) {
      if (eventListener != null && batchSelected.isEmpty()) {
        eventListener.onInviteSharedContactClicked(choices);
      } else {
        passthroughClickListener.onClick(sharedContactStub.get());
      }
    }

    @Override
    public void onMessageClicked(@NonNull List<Recipient> choices) {
      if (eventListener != null && batchSelected.isEmpty()) {
        eventListener.onMessageSharedContactClicked(choices);
      } else {
        passthroughClickListener.onClick(sharedContactStub.get());
      }
    }
  }

  private class SharedContactClickListener implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      if (eventListener != null && batchSelected.isEmpty() && messageRecord.isMms() && !((MmsMessageRecord) messageRecord).getSharedContacts().isEmpty()) {
        eventListener.onSharedContactDetailsClicked(((MmsMessageRecord) messageRecord).getSharedContacts().get(0), sharedContactStub.get().getAvatarView());
      } else {
        passthroughClickListener.onClick(view);
      }
    }
  }

  private class AttachmentDownloadClickListener implements SlideClickListener {
    @Override
    public void onClick(View v, final Slide slide) {
      if (messageRecord.isMmsNotification()) {
        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new MmsDownloadJob(context, messageRecord.getId(),
                                                  messageRecord.getThreadId(), false));
      } else {
        DatabaseFactory.getAttachmentDatabase(context).setTransferState(messageRecord.getId(),
                                                                        slide.asAttachment(),
                                                                        AttachmentDatabase.TRANSFER_PROGRESS_STARTED);

        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new AttachmentDownloadJob(context, messageRecord.getId(),
                                                         ((DatabaseAttachment)slide.asAttachment()).getAttachmentId(), true));
      }
    }
  }

  private class ThumbnailClickListener implements SlideClickListener {
    public void onClick(final View v, final Slide slide) {
      if (shouldInterceptClicks(messageRecord) || !batchSelected.isEmpty()) {
        performClick();
      } else if (MediaPreviewActivity.isContentTypeSupported(slide.getContentType()) && slide.getUri() != null) {
        Intent intent = new Intent(context, MediaPreviewActivity.class);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(slide.getUri(), slide.getContentType());
        intent.putExtra(MediaPreviewActivity.ADDRESS_EXTRA, conversationRecipient.getAddress());
        intent.putExtra(MediaPreviewActivity.OUTGOING_EXTRA, messageRecord.isOutgoing());
        intent.putExtra(MediaPreviewActivity.DATE_EXTRA, messageRecord.getTimestamp());
        intent.putExtra(MediaPreviewActivity.SIZE_EXTRA, slide.asAttachment().getSize());
        intent.putExtra(MediaPreviewActivity.LEFT_IS_RECENT_EXTRA, false);

        context.startActivity(intent);
      } else if (slide.getUri() != null) {
        Log.w(TAG, "Clicked: " + slide.getUri() + " , " + slide.getContentType());
        Uri publicUri = PartAuthority.getAttachmentPublicUri(slide.getUri());
        Log.w(TAG, "Public URI: " + publicUri);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(PartAuthority.getAttachmentPublicUri(slide.getUri()), slide.getContentType());
        try {
          context.startActivity(intent);
        } catch (ActivityNotFoundException anfe) {
          Log.w(TAG, "No activity existed to view the media.");
          Toast.makeText(context, R.string.ConversationItem_unable_to_open_media, Toast.LENGTH_LONG).show();
        }
      }
    }
  }

  private class PassthroughClickListener implements View.OnLongClickListener, View.OnClickListener {

    @Override
    public boolean onLongClick(View v) {
      if (bodyText.hasSelection()) {
        return false;
      }
      performLongClick();
      return true;
    }

    @Override
    public void onClick(View v) {
      performClick();
    }
  }

  private class ClickListener implements View.OnClickListener {
    private OnClickListener parent;

    ClickListener(@Nullable OnClickListener parent) {
      this.parent = parent;
    }

    public void onClick(View v) {
      if (!shouldInterceptClicks(messageRecord) && parent != null) {
        parent.onClick(v);
      } else if (messageRecord.isFailed()) {
        Intent intent = new Intent(context, MessageDetailsActivity.class);
        intent.putExtra(MessageDetailsActivity.MESSAGE_ID_EXTRA, messageRecord.getId());
        intent.putExtra(MessageDetailsActivity.THREAD_ID_EXTRA, messageRecord.getThreadId());
        intent.putExtra(MessageDetailsActivity.TYPE_EXTRA, messageRecord.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
        intent.putExtra(MessageDetailsActivity.IS_PUSH_GROUP_EXTRA, groupThread && messageRecord.isPush());
        intent.putExtra(MessageDetailsActivity.ADDRESS_EXTRA, conversationRecipient.getAddress());
        context.startActivity(intent);
      } else if (!messageRecord.isOutgoing() && messageRecord.isIdentityMismatchFailure()) {
        handleApproveIdentity();
      } else if (messageRecord.isPendingInsecureSmsFallback()) {
        handleMessageApproval();
      }
    }
  }

  private void handleMessageApproval() {
    final int title;
    final int message;

    if (messageRecord.isMms()) title = R.string.ConversationItem_click_to_approve_unencrypted_mms_dialog_title;
    else                       title = R.string.ConversationItem_click_to_approve_unencrypted_sms_dialog_title;

    message = R.string.ConversationItem_click_to_approve_unencrypted_dialog_message;

    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(title);

    if (message > -1) builder.setMessage(message);

    builder.setPositiveButton(R.string.yes, (dialogInterface, i) -> {
      if (messageRecord.isMms()) {
        MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
        database.markAsInsecure(messageRecord.getId());
        database.markAsOutbox(messageRecord.getId());
        database.markAsForcedSms(messageRecord.getId());

        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new MmsSendJob(context, messageRecord.getId()));
      } else {
        SmsDatabase database = DatabaseFactory.getSmsDatabase(context);
        database.markAsInsecure(messageRecord.getId());
        database.markAsOutbox(messageRecord.getId());
        database.markAsForcedSms(messageRecord.getId());

        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new SmsSendJob(context, messageRecord.getId(),
                                              messageRecord.getIndividualRecipient().getAddress().serialize()));
      }
    });

    builder.setNegativeButton(R.string.no, (dialogInterface, i) -> {
      if (messageRecord.isMms()) {
        DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageRecord.getId());
      } else {
        DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageRecord.getId());
      }
    });
    builder.show();
  }
}
