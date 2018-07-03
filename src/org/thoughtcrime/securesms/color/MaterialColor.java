package org.thoughtcrime.securesms.color;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.R;

import java.util.HashMap;
import java.util.Map;

import static org.thoughtcrime.securesms.util.ThemeUtil.isDarkTheme;

public enum MaterialColor {
  RED        (R.color.conversation_red,         R.color.red_900,         "red"),
  PINK       (R.color.conversation_pink,        R.color.pink_800,        "pink"),
  PURPLE     (R.color.conversation_purple,      R.color.purple_700,      "purple"),
  INDIGO     (R.color.conversation_indigo,      R.color.indigo_800,      "indigo"),
  BLUE       (R.color.conversation_blue,        R.color.blue_900,        "blue"),
  CYAN       (R.color.conversation_cyan,        R.color.cyan_900,        "cyan"),
  TEAL       (R.color.conversation_teal,        R.color.teal_800,        "teal"),
  GREEN      (R.color.conversation_green,       R.color.green_900,       "green"),
  ORANGE     (R.color.conversation_orange,      R.color.deep_orange_900, "orange"),
  GREY       (R.color.conversation_grey,        R.color.grey_700,        "grey");

  private static Map<String, MaterialColor> colorMap = new HashMap<String, MaterialColor>() {{
    put("red", RED);
    put("brown", RED);
    put("pink", PINK);
    put("purple", PURPLE);
    put("deep_purple", PURPLE);
    put("indigo", INDIGO);
    put("blue", BLUE);
    put("light_blue", BLUE);
    put("cyan", CYAN);
    put("blue_grey", CYAN);
    put("teal", TEAL);
    put("green", GREEN);
    put("light_green", GREEN);
    put("lime", GREEN);
    put("orange", ORANGE);
    put("amber", ORANGE);
    put("deep_orange", ORANGE);
    put("yellow", ORANGE);
    put("grey", GREY);
    put("group_color", BLUE);
  }};

  private final int conversationColorLight;
  private final int actionBarColorLight;
  private final int statusBarColorLight;
  private final int conversationColorDark;
  private final int actionBarColorDark;
  private final int statusBarColorDark;
  private final String serialized;

  MaterialColor(int conversationColorLight, int actionBarColorLight,
                int statusBarColorLight, int conversationColorDark,
                int actionBarColorDark, int statusBarColorDark,
                String serialized)
  {
    this.conversationColorLight = conversationColorLight;
    this.actionBarColorLight    = actionBarColorLight;
    this.statusBarColorLight    = statusBarColorLight;
    this.conversationColorDark  = conversationColorDark;
    this.actionBarColorDark     = actionBarColorDark;
    this.statusBarColorDark     = statusBarColorDark;
    this.serialized             = serialized;
  }

  MaterialColor(int conversationColor, int statusBarColor, String serialized)
  {
    this(conversationColor, conversationColor, statusBarColor,
         conversationColor, conversationColor, statusBarColor, serialized);
  }

  public int toConversationColor(@NonNull Context context) {
    return context.getResources().getColor(isDarkTheme(context) ? conversationColorDark
                                                                : conversationColorLight);
  }

  public int toActionBarColor(@NonNull Context context) {
    return context.getResources().getColor(isDarkTheme(context) ? actionBarColorDark
                                                                : actionBarColorLight);
  }

  public int toStatusBarColor(@NonNull Context context) {
    return context.getResources().getColor(isDarkTheme(context) ? statusBarColorDark
                                                                : statusBarColorLight);
  }

  public int toQuoteBarColorResource(@NonNull Context context, boolean outgoing) {
    if (outgoing) {
      return conversationColorDark;
    }
    return R.color.white;
  }

  public int toQuoteBackgroundColor(@NonNull Context context, boolean outgoing) {
    if (outgoing) {
      if (isDarkTheme(context)) {
        return context.getResources().getColor(R.color.transparent_white_60);
      } else {
        int color = toConversationColor(context);
        return Color.argb(0x44, Color.red(color), Color.green(color), Color.blue(color));
      }
    }
    return context.getResources().getColor(isDarkTheme(context) ? R.color.transparent_white_70
                                                                : R.color.transparent_white_aa);
  }

  public int toQuoteIconForegroundColor(@NonNull Context context, boolean outgoing) {
    if (outgoing) {
      return context.getResources().getColor(R.color.white);
    }
    return toConversationColor(context);
  }

  public int toQuoteIconBackgroundColor(@NonNull Context context, boolean outgoing) {
    return context.getResources().getColor(toQuoteBarColorResource(context, outgoing));
  }

  public boolean represents(Context context, int colorValue) {
    return context.getResources().getColor(conversationColorDark)  == colorValue ||
           context.getResources().getColor(conversationColorLight) == colorValue ||
           context.getResources().getColor(actionBarColorDark) == colorValue ||
           context.getResources().getColor(actionBarColorLight) == colorValue ||
           context.getResources().getColor(statusBarColorLight) == colorValue ||
           context.getResources().getColor(statusBarColorDark) == colorValue;
  }

  public String serialize() {
    return serialized;
  }

  public static MaterialColor fromSerialized(String serialized) throws UnknownColorException {
    if (colorMap.containsKey(serialized)) {
      return colorMap.get(serialized);
    }

    throw new UnknownColorException("Unknown color: " + serialized);
  }

  public static class UnknownColorException extends Exception {
    public UnknownColorException(String message) {
      super(message);
    }
  }

}
