package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;

import pl.tajchert.sample.DotsTextView;

public class DeliveryStatusView extends LinearLayout {

  private static final String TAG = DeliveryStatusView.class.getSimpleName();

  private final TextView  deliveryText;
  private final ViewGroup pendingIndicatorStub;
  private final ImageView sentIndicator;
  private final ImageView deliveredIndicator;
  private final ImageView readIndicator;

  public DeliveryStatusView(Context context) {
    this(context, null);
  }

  public DeliveryStatusView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public DeliveryStatusView(final Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);

    inflate(context, R.layout.delivery_status_view, this);

    this.deliveryText         = findViewById(R.id.delivery_text);
    this.deliveredIndicator   = findViewById(R.id.delivered_indicator);
    this.sentIndicator        = findViewById(R.id.sent_indicator);
    this.pendingIndicatorStub = findViewById(R.id.pending_indicator_stub);
    this.readIndicator        = findViewById(R.id.read_indicator);

    int iconColor = Color.GRAY;

    if (Build.VERSION.SDK_INT >= 11) {
      inflate(context, R.layout.conversation_item_pending_v11, pendingIndicatorStub);
      DotsTextView pendingIndicator = findViewById(R.id.pending_indicator);
      pendingIndicator.setDotsColor(iconColor);
    } else {
      inflate(context, R.layout.conversation_item_pending, pendingIndicatorStub);
      TextView pendingIndicator = findViewById(R.id.pending_indicator);
      pendingIndicator.setTextColor(iconColor);
    }
  }

  public void setNone() {
    this.setVisibility(View.GONE);
  }

  public void setPending() {
    this.setVisibility(View.VISIBLE);
    deliveryText.setText(R.string.DeliveryStatus_sending);
    pendingIndicatorStub.setVisibility(View.VISIBLE);
    sentIndicator.setVisibility(View.GONE);
    deliveredIndicator.setVisibility(View.GONE);
    readIndicator.setVisibility(View.GONE);
  }

  public void setSent() {
    this.setVisibility(View.VISIBLE);
    deliveryText.setText(R.string.DeliveryStatus_sent);
    pendingIndicatorStub.setVisibility(View.GONE);
    sentIndicator.setVisibility(View.VISIBLE);
    deliveredIndicator.setVisibility(View.GONE);
    readIndicator.setVisibility(View.GONE);
  }

  public void setDelivered() {
    this.setVisibility(View.VISIBLE);
    deliveryText.setText(R.string.DeliveryStatus_delivered);
    pendingIndicatorStub.setVisibility(View.GONE);
    sentIndicator.setVisibility(View.GONE);
    deliveredIndicator.setVisibility(View.VISIBLE);
    readIndicator.setVisibility(View.GONE);
  }

  public void setRead() {
    this.setVisibility(View.VISIBLE);
    deliveryText.setText(R.string.DeliveryStatus_read);
    pendingIndicatorStub.setVisibility(View.GONE);
    sentIndicator.setVisibility(View.GONE);
    deliveredIndicator.setVisibility(View.GONE);
    readIndicator.setVisibility(View.VISIBLE);
  }
}
