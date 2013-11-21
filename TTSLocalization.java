package com.comcast.guide.tts;
/**
 * Created with IntelliJ IDEA.
 * User: jzhao200
 * Date: 7/30/13
 * Time: 1:18 PM
 * To change this template use File | Settings | File Templates.
 */


import com.comcast.guide.data.GuideTextToSpeechController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.ResourceBundle;

public class TTSLocalization {
    public static Logger log = LoggerFactory.getLogger(TTSLocalization.class);

    public enum    LocalStrings {
        SELECTED,
        CHANGE_SAP_LANGUAGE,
        LEFT,
        HOURS,
        HOUR,
        SAT,
        DAY,
        RECORDINGS_ADDED,
        TTS_Exception,
        GUIDE_INIT,
        S,
        GUIDE,
        ON,
        OFF,
        CATEGORIES_LIST,
        PREVIEW,
        NAV_OPTIONS_GUIDE,
        RELEASE_YEAR,
        PERCENT,
        TOMATOES_RATING,
        XFINITY_TV_LISTINGS,
        GRID,
        REAMAINING,
        RATED,
        HD_PROGRAM,
        ZERO,
        WATCH_FREE,
        AIR_DATE,
        MOVE_INSTRUCTION,
        HORIZONTAL_INSTRUCTION,
        VERTICAL_INSTRUCTION,
        X2_HORIZONTAL_INSTRUCTION,
        COMMA,
        OF,
        COLUMN,
        ROW,
        NEXT_LINE,
        TRUE,
        VOICE_GUIDED_NAVIGATION,
        XFINITY_VOICE_GUIDED_NAVIGATION,
        DOT,
        CARRIAGE_RETURN,
        PRESS_MENU_BUTTON_ACCESS,
        MINUTES,
        MINUTE,
        CHANNEL,
        TILE_BADGE_NEW_ARRIVAL,
        ENDS_SOON,
        FEATURED,
        PATTERN1,
        PATTERN2,
        PATTERN3,
        SEASON,
        EPISODE,
        EPISODES,
        EPISODE1,
        TO,
        WEDNESDAY,
        NOT_AVAILABLE,
        VIEW_ALL,
        RECORDING_NOW,
        RECORDED,
        AM,
        PM,
        MAIN_MENU;

        public String getLocalString(ResourceBundle localization) {
            return localization.getString(toString());
        }

        public String getLocalString(GuideTextToSpeechController app){
            return getLocalString(app.getTTSLocalization());
        }
        public String formatMessage(ResourceBundle localization, Object... args) {
            return formatMessage(localization, "undefined", args);
        }

        protected String formatMessage(ResourceBundle localization, String logSource, Object... args) {
            String result = "";
            try {
                MessageFormat formatter = new MessageFormat("");
                formatter.setLocale(localization.getLocale());
                formatter.applyPattern(localization.getString(this.toString()));
                result = formatter.format(args);
            } catch (Exception ex) {
                log.error("event=formatMessage_for_" + logSource + " status=exception tokens=" + args, ex);
            }
            return result;
        }

    }
}
