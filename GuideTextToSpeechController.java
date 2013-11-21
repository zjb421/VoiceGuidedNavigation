package com.comcast.guide.data;

import com.comcast.data.texttospeech.ITextToSpeechService;
import com.comcast.guide.GuideConstants;
import com.comcast.guide.tts.TTSLocalization;
import com.comcast.player.RemoteShellPlayerProxy;
import com.comcast.xre.XREApplication;
import com.comcast.xre.XREView;
import com.comcast.xre.events.IXREEventListener;
import com.comcast.xre.events.XREErrorInfo;
import com.comcast.xre.events.XREEventInfo;
import com.comcast.xre.events.XREResourceInfo;
import com.comcast.xre.resource.XRESoundResource;
import com.comcast.xre.resource.view.XREHTMLTextResource;
import com.comcast.xre.resource.view.XRETextResource;
import com.comcast.xre.services.ITextToSpeechController;
import com.comcast.xre.toolkit.*;
import com.comcast.xre.toolkit.presentation.model.AbstractModel;
import com.comcast.xre.toolkit.presentation.model.DataModelEntry;
import com.comcast.xre.toolkit.presentation.model.ModuleModel;
import com.comcast.xre.toolkit.presentation.view.SimpleModuleView;
import com.comcast.xre.toolkit.scroll.BasicVirtualizer;
import com.comcast.xre.toolkit.x2.module.tile.EpisodicMetadataTile;
import com.hercules.serverdata.data.model.Channel;
import com.hercules.serverdata.data.model.SEAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class GuideTextToSpeechController implements IGuideTextToSpeechController {
    XREApplication app;
    ITextToSpeechService service;
    RemoteShellPlayerProxy player;
    XRESoundResource audio;
    boolean audioPlaying = false;
    public static ResourceBundle ttsLocalization;

    boolean enabled = false;
    boolean activated = false;

    int currentRequestIdx = 0;
    int playingRequestIdx = 0;

    public static final String TTS_PROPERTIES = "com.comcast.guide.tts.localization_tts";

    long starttime;

    List<String> speechBuffer = new ArrayList<String>();

    public static Logger log = LoggerFactory.getLogger(GuideTextToSpeechController.class);

    public void initLocalization()
    {

        Locale loc = new Locale("en", "US");
        ttsLocalization = ResourceBundle.getBundle(TTS_PROPERTIES, loc);

    }

    public GuideTextToSpeechController(XREApplication app,
                                       ITextToSpeechService service,
                                       RemoteShellPlayerProxy player)
    {
        initLocalization();
        this.app = app;
        this.service = service;
        this.player = player;
    }

    private void constructSoundResource(String url)
    {
        log.info("constructTimeResourceElapsedTime=" + (new Date().getTime() - starttime) + " ; constructSoundResource=" + url);
        audio = new XRESoundResource(app, url, false);
        audioPlaying = true;

        audio.getOnStreamComplete().addListener(new IXREEventListener<XREEventInfo>() {
            public boolean onEvent(XREEventInfo event) {
                //Audio complete - stop/unmute
                log.info("audioOnStreamCompleteElapsedTime=" + (new Date().getTime() - starttime) + " ; audio complete");
                stop();

                if (!speechBuffer.isEmpty())
                {
                    String speakText = speechBuffer.remove(0);
                    String audioURL = service.getTextToSpeechAudioURL(speakText);
                    if (audioURL !=null)
                        constructSoundResource(audioURL);
                    else
                    {
                        audioPlaying = false;
                        speechBuffer.clear();
                    }
                }
                else
                {
                    audioPlaying = false;
                }

                return true;
            }
        });

        /*
        audio.getOnStreamBuffering().addListener(new IXREEventListener<XREEventInfo>() {
            public boolean onEvent(XREResourceInfo event) {
                //Audio loaded check to see if another request has come in.
                if (playingRequestIdx != currentRequestIdx)
                {
                    log.info("not matching IDX, stopping");
                    stop();
                }
                else
                {
                    log.info("mute");
                    handlePlayerAudio();
                    audio.play();
                }
                return true;
            }
        });   */

        audio.getOnStreamBuffering().addListener(new IXREEventListener<XREEventInfo>() {
            public boolean onEvent(XREEventInfo event) {
                log.info("audioOnStreamBufferingElapsedTime=" + (new Date().getTime() - starttime));
                return true;
            }
        });

        audio.getOnError().addListener(new IXREEventListener<XREErrorInfo>() {

            public boolean onEvent(XREErrorInfo event) {
                log.error("ERROR: " + event.getErrorType() + " " + event.getDescription());
                return true;
            }
        });

        audio.getOnReady().addListener(new IXREEventListener<XREResourceInfo>() {
            public boolean onEvent(XREResourceInfo event) {
                //Audio loaded check to see if another request has come in.
                if (playingRequestIdx != currentRequestIdx)
                {
                    log.info("TTS not matching IDX, stopping");
                    stop();
                }
                else
                {
                    log.info("audioOnReadyElapsedTime=" + (new Date().getTime() - starttime) + " ; mute and attempting to play");
                    handlePlayerAudio();
                    audio.play();
                }
                return true;
            }
        });

    }

    //Activated means the functionality is available -- currently done by an Easter Egg (Menu 2-4-6-8)
    //Enabled means currently "on", which occurs when the user presses Soft Key #4 in an activated Guide

    @Override
    public void setActivated(boolean activated)
    {
        this.activated = activated;
    }

    @Override
    public String getGuideMainMenuModeText(ToolkitApplication app) {
        ITextToSpeechController controller = (ITextToSpeechController) app.getService(ITextToSpeechController.class);
        if (controller != null && controller.getEnabled())
        {
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append(TTSLocalization.LocalStrings.GUIDE.getLocalString(controller.getTTSLocalization()));
            stringBuffer.append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(controller.getTTSLocalization()));
            //stringBuffer.append(position).append(" ").append(TTSLocalization.LocalStrings.OF.getLocalString(ttsLocalization)).append(" ").append(totalCount);
            stringBuffer.append(TTSLocalization.LocalStrings.GUIDE_INIT.getLocalString(controller.getTTSLocalization()));
            stringBuffer.append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(controller.getTTSLocalization()));
            stringBuffer.append(controller.createEpilogueText(ITextToSpeechController.TextToSpeech.Epilogue.HORIZONTAL_INSTRUCTION));
            return stringBuffer.toString();
        }
        else
            return null;
    }


    public String getICEListCellText(ChoosableText col2Row1Txt,ChoosableText col2Row2Txt,ChoosableText col3Row1Txt,ChoosableText col3Row2Txt) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(col2Row1Txt.get()+ TTSLocalization.LocalStrings.CARRIAGE_RETURN.toString() +col2Row2Txt.get()+TTSLocalization.LocalStrings.CARRIAGE_RETURN.toString()+col3Row1Txt.get()+TTSLocalization.LocalStrings.CARRIAGE_RETURN.toString()+col3Row2Txt.get());
        return stringBuffer.toString();
    }

    @Override
    public String getCommandIconListCellText(ChoosableText txt) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(txt.get());
        return stringBuffer.toString();
    }

    @Override
    public String getFilmstripText(PanelView titlePanel) {
        XRETextResource r = (XRETextResource) titlePanel.getResource();
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(r.getText());
        return stringBuffer.toString();
    }

    public String getPanelViewText(XRETextResource txtResource) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(txtResource.getText());
        return stringBuffer.toString();
    }

    @Override
    public String getSpeakableTextPanelText() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getHeaderModuleText(List<String> pathStack) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(pathStack.get(pathStack.size()-1));
        return stringBuffer.toString();    }

    @Override
    public String getCommandListCellText(ChoosableText txt) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(txt.get());
        return stringBuffer.toString();    }

    @Override
    public ResourceBundle getTTSLocalization() {

        Locale loc = new Locale("en", "US");
        ttsLocalization = ResourceBundle.getBundle(TTS_PROPERTIES, loc);
        return ttsLocalization;
    }


    @Override
    public boolean getActivated()
    {
        return activated;
    }

    @Override
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    @Override
    public boolean getEnabled()
    {
        return enabled;
    }

    @Override
    public void speak(String speakText)
    {
        speak(speakText, false);
    }

    public void speak(String speakText, boolean appendToLast)
    {
        if (!enabled || !activated || speakText == null)
            return;
        else
        {
            starttime = new Date().getTime();
            log.info("speak called - marking time");

            if (appendToLast && audioPlaying)
                speechBuffer.add(speakText);
            else
            {
                stop();
                updateRequestId();
                speechBuffer.clear();

                String audioURL = service.getTextToSpeechAudioURL(speakText);
                if (audioURL !=null)
                    constructSoundResource(audioURL);
            }
        }
    }

    @Override
    public void stop()
    {
        log.info("audioStopElapsedTime: " + (new Date().getTime() - starttime));
        if (!enabled)
            return;
        else
        {
            audioPlaying = false;
            if (audio != null)
            {
                log.info("Deleting TTS audio resource");
                audio.pause();
                audio.delete();
                audio = null;
            }
            restorePlayerAudio();
        }
    }

    @Override
    public void interrupt()
    {
        if (audioPlaying)
            stop();
    }

    private void updateRequestId()
    {
        if (currentRequestIdx + 1 > Integer.MAX_VALUE)
            currentRequestIdx = 0;

        currentRequestIdx++;
        playingRequestIdx = currentRequestIdx;
        log.info("requestID" + currentRequestIdx);
    }

    @Override
    public void speakView(XREView view)
    {
        //String speech = recursiveText(view);
        String speech = genericRecursiveText(view);
        if (speech != null & !speech.equals(""))
            speak(speech);
    }

    @Override
    public String recursiveText(XREView view)
    {
        StringBuffer sb = new StringBuffer();

        if (view instanceof SpeakableTextPanel)
            sb.append(((SpeakableTextPanel) view).getSpeakableText()).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.toString());

        //Base Case
        if (view.getChildCount() == 0)
        {
            if (sb.length() > 0)
                return sb.toString();
            else
                return null;
        }
        else
        {
            for (XREView child : view.getChildren())
            {
                String recursiveText = recursiveText(child);
                if (recursiveText != null && !recursiveText.equals(""))
                    sb.append(recursiveText);
            }
            return sb.toString();
        }
    }

    @Override
    public String genericRecursiveText(XREView view)
    {
        StringBuffer sb = new StringBuffer();

        if (view instanceof PanelView)
        {
            PanelView panel = (PanelView) view;
            if (panel.getAccessibilityText() != null)
                sb.append(panel.getAccessibilityText()).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.toString());
        }

        //Base Case
        if (view.getChildCount() == 0)
        {
            if (sb.length() > 0)
                return sb.toString();
            else
                return null;
        }
        else
        {
            for (XREView child : view.getChildren())
            {
                String recursiveText = recursiveText(child);
                if (recursiveText != null && !recursiveText.equals(""))
                    sb.append(recursiveText);
            }
            return sb.toString();
        }
    }

    private void handlePlayerAudio()
    {
        player.mute();
        //player.setVolume(10);
    }

    private void restorePlayerAudio()
    {
        player.unmute();
        //player.setVolume(100);
    }

    @Override
    public void listPresented(String prologue, List<String> list, int chosenItem, TextToSpeech.Epilogue epilogue)
    {
        StringBuffer accessibilityText = new StringBuffer();

        accessibilityText.append(prologue).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.toString());

        accessibilityText.append(createListText(list, chosenItem));

        String epilogueText = createEpilogueText(epilogue);
        if (epilogueText != null)
            accessibilityText.append(epilogueText);

        speak(accessibilityText.toString());
    }

    @Override
    public void listItemChanged(List<String> list, int chosenItem)
    {
        speak(createListText(list, chosenItem));
    }


    @Override
    public void dataPresented(List<String> data)
    {
        dataPresented(data, false);
    }

    private void dataPresented(List<String> data, boolean appendToPreviousAudio)
    {
        StringBuffer buffer = new StringBuffer();
        for (String string : data)
            buffer.append(string).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.toString());

        speak(buffer.toString());
    }


    @Override
    public String create2DListText(List<String> list, int chosenItem, int chosenRow, int totalRows)
    {
        StringBuffer returnBuffer = new StringBuffer();
        returnBuffer.append(list.get(chosenItem)).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        returnBuffer.append(TTSLocalization.LocalStrings.ROW.getLocalString(ttsLocalization)).append(chosenRow + 1).append(TTSLocalization.LocalStrings.OF.getLocalString(ttsLocalization)).append(totalRows).append(TTSLocalization.LocalStrings.COMMA.getLocalString(ttsLocalization)).append(" ");
        returnBuffer.append(TTSLocalization.LocalStrings.COLUMN.getLocalString(ttsLocalization)).append(chosenItem+1).append(TTSLocalization.LocalStrings.OF.getLocalString(ttsLocalization)).append(list.size()).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        return returnBuffer.toString();
    }

    @Override
    public String createListText(List<String> list, int chosenItem)
    {
        return list.get(chosenItem) + TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization) + (chosenItem+1) + " " + TTSLocalization.LocalStrings.OF.getLocalString(ttsLocalization) + " " + list.size() + TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization);
    }

    @Override
    public String createEpilogueText(TextToSpeech.Epilogue epilogue)
    {
        switch (epilogue)
        {
            case HORIZONTAL_INSTRUCTION:
                return TTSLocalization.LocalStrings.HORIZONTAL_INSTRUCTION.getLocalString(ttsLocalization);

            case VERTICAL_INSTRUCTION:
                return TTSLocalization.LocalStrings.VERTICAL_INSTRUCTION.getLocalString(ttsLocalization);

            case X2_HORIZONTAL_INSTRUCTION:
                return TTSLocalization.LocalStrings.HORIZONTAL_INSTRUCTION.getLocalString(ttsLocalization);
            case MOVE_INSTRUCTION:
                return TTSLocalization.LocalStrings.MOVE_INSTRUCTION.getLocalString(ttsLocalization);
        }
        return null;
    }

    @Override
    public String getAccessibilityText() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    private static final long PERIOD = GuideConstants.HOUR_IN_MILLIS * 24 * 7; // 7 days

    @Override
    public String createEntityInfoText(Entity entity)
    {
       return createEntityInfoText(entity, true);
    }

    @Override
    public String createEntityInfoText(Entity entity, boolean includeDescription)
    {
        StringBuffer buffer = new StringBuffer();
        //buffer.append(entity.getEntityTitle() + CARRIAGE_RETURN);

        /*int baseStars = (int) entity.getUserRating();
        buffer.append(baseStars);
        buffer.append(" star");

        if (baseStars > 1)
            buffer.append("s");

        buffer.append(CARRIAGE_RETURN);*/

        if (entity.getOriginalAirDate() != null)
        {
            Calendar cal = Calendar.getInstance();
            cal.setTime(entity.getOriginalAirDate());
            DateFormat formatter = new SimpleDateFormat("MMMMM dd yyyy");
            buffer.append(TTSLocalization.LocalStrings.AIR_DATE.getLocalString(ttsLocalization)).append(" ").append(formatter.format(cal.getTime()));
            buffer.append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));
        }
        else if (entity.getYear() != null)
            buffer.append(entity.getYear() + TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        else
        {
            //Year may be in Series
            if (entity.getSeries() != null)
                if (entity.getSeries().getYear() != 0)
                    buffer.append(entity.getSeries().getYear() + TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));
        }

        if (entity.getPrice() != null)
        {
            if (entity.getPrice().equals(TTSLocalization.LocalStrings.ZERO.getLocalString(ttsLocalization)))
               buffer.append(TTSLocalization.LocalStrings.WATCH_FREE.getLocalString(ttsLocalization) + TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));
            else
                buffer.append(entity.getPrice() + TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));
        }

        if (!entity.getFormattedDuration().equals(""))
            buffer.append(entity.getFormattedDuration().replace("min", TTSLocalization.LocalStrings.MINUTES.getLocalString(ttsLocalization)) + TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        if (entity.getAttributes() != null && entity.getAttributes().isAttributeSet(SEAttributes.Attribute.HDTV))
            buffer.append (TTSLocalization.LocalStrings.HD_PROGRAM.getLocalString(ttsLocalization) + TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        if (entity.getRating() !=null)
            buffer.append(TTSLocalization.LocalStrings.RATED.getLocalString(ttsLocalization) + " " + entity.getRating() + TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        Date now = new Date();
        if (entity.getAvailableDate() != null) {
            if (now.getTime() - entity.getAvailableDate().getTime() < PERIOD) {
                buffer.append (TTSLocalization.LocalStrings.TILE_BADGE_NEW_ARRIVAL.getLocalString(ttsLocalization) + TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));
            }
        }
        else if (entity.getExpirationDate() != null) {
            if (entity.getExpirationDate().getTime() - now.getTime() < PERIOD) {
            }
        }

        if (entity.getDescription() != null && includeDescription)
            buffer.append(entity.getDescription());

        return buffer.toString();
    }

    @Override
    public String createListingText(Channel channel, Date startTime, Date endTime, String title, boolean betweenChannels)
    {
        StringBuffer returnBuffer = new StringBuffer();
        Date now = new Date();
        boolean onNow = false;

        if (startTime.before(now) && endTime.after(now))
        {
            onNow = true;
        } else
        {
            returnBuffer.append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));
        }

        returnBuffer.append(title).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        if (onNow)
            returnBuffer.append(calculateDuration(endTime, now)).append(" ").append(TTSLocalization.LocalStrings.REAMAINING.getLocalString(ttsLocalization));
        else
            returnBuffer.append(calculateDuration(endTime, startTime));

        if (betweenChannels)
        {
            if (channel.getChannelNumber() != null)
            {
                returnBuffer.append(createChannelString(Integer.parseInt(channel.getChannelNumber())));
            }

            if (channel.getCallSign() != null)
                returnBuffer.append(channel.getCallSign());

            returnBuffer.append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));
        }

        return returnBuffer.toString();
    }


    public static String createChannelString(int channelNumber)
    {
        StringBuffer returnBuffer = new StringBuffer();
        returnBuffer.append(TTSLocalization.LocalStrings.CHANNEL.getLocalString(ttsLocalization));
        returnBuffer.append(" ");
        returnBuffer.append(channelNumber).append(" ");
        return returnBuffer.toString();
    }

    public static String calculateDuration(Date first, Date second)
    {
        StringBuffer returnBuffer = new StringBuffer();
        long difference = first.getTime() - second.getTime();
        difference = difference / 60 / 1000;
        returnBuffer.append(difference);
        returnBuffer.append(TTSLocalization.LocalStrings.MINUTE.getLocalString(ttsLocalization));
        if (difference > 1)
            returnBuffer.append( TTSLocalization.LocalStrings.MINUTES.getLocalString(ttsLocalization));
        else
            returnBuffer.append( TTSLocalization.LocalStrings.MINUTE.getLocalString(ttsLocalization));

        return returnBuffer.toString();
    }

    @Override
    public String getName() {
        return "GuideTextToSpeechController";
    }

    @Override
    public String getAccessilityText() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }


    public String getPositionText(int position, int totalCount)
    {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(position).append(" ").append(TTSLocalization.LocalStrings.OF.getLocalString(ttsLocalization)).append(" ").append(totalCount);
        return stringBuffer.toString();
    }

    public String getMovieTileText(DataModelEntry data, int position, int totalCount)
    {
        long PERIOD = 1000l * 60 * 60 * 24 * 7; // 7 days

        StringBuffer buffer = new StringBuffer();
        String title = data.getTitle();
        if (title == null)
            title = data.getStringParam(DataParamName.title);
        if (title != null)
            buffer.append(title).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        //buffer.append(getPositionText(position, totalCount)).append(CARRIAGE_RETURN);

        int criticsRating = data.getIntParam(DataParamName.criticsRating, -1);
        if (criticsRating != -1)
            buffer.append(TTSLocalization.LocalStrings.TOMATOES_RATING.getLocalString(ttsLocalization)).append(" ").append(criticsRating).append(TTSLocalization.LocalStrings.PERCENT.getLocalString(ttsLocalization)).append(" ").append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        String year = data.getStringParam(DataParamName.year);
        if (year != null)
            buffer.append(TTSLocalization.LocalStrings.RELEASE_YEAR.toString()).append(" ").append(year).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        String price = data.getStringParam(DataParamName.price);
        if (price != null)
            buffer.append(price).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        int duration = data.getIntParam(DataParamName.duration, -1);
        if (duration != -1)
            buffer.append(duration).append(" ").append(TTSLocalization.LocalStrings.MINUTES).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        String rating = data.getStringParam(DataParamName.rating);
        if (rating != null)
            buffer.append(TTSLocalization.LocalStrings.RATED.toString()).append(" ").append(rating).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        Date availabilityDate = (Date)data.getParam(DataParamName.availDate);
        Date expirationDate = (Date)data.getParam(DataParamName.expireDate);
        if (availabilityDate != null && expirationDate != null)
        {
            Date now = new Date();
            try {
                if (now.getTime() - availabilityDate.getTime() < PERIOD)
                {
                    buffer.append(TTSLocalization.LocalStrings.TILE_BADGE_NEW_ARRIVAL.toString()).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));
                } else if (expirationDate.getTime() - now.getTime() < PERIOD)
                {
                    buffer.append(TTSLocalization.LocalStrings.ENDS_SOON.toString() + expirationDate).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));
                }
            }
            catch (Exception e){
            }
        }

        return buffer.toString();
    }

    public String getRowControlPresentationText(AbstractModel model,Choice<ModuleModel> choice,List<SimpleModuleView> modules,BasicVirtualizer<ModuleModel> virtualizer)
    {

        StringBuffer stringBuffer = new StringBuffer();

        String title = choice.getChosen().getStringParam(ModuleParamName.title);
        if (title != null)
            stringBuffer.append(title).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        if (model.getName() != null)
            stringBuffer.append(model.getName()).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        String moduleText = modules.get(virtualizer.choiceIndexToViewIndex(choice.getChosenIndex())).getAccessibilityText();
        if (moduleText != null)
            stringBuffer.append(moduleText);

        if (stringBuffer.length() > 0)
            return stringBuffer.toString();
        else
            return null;
    }

    public String getAbstractTileText() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getActionBarText() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getEpisodicMetadataTile(int position, int totalCount) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getStationTile(int position, int totalCount) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getSeriesText(DataModelEntry data,int position, int totalCount)
    {


        StringBuffer buffer = new StringBuffer();

        String title = data.getTitle();
        if (title == null)
            title = data.getStringParam(DataParamName.title);
        if (title != null)
            buffer.append(title).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));

        //buffer.append(getPositionText(position,totalCount)).append(CARRIAGE_RETURN);

        if (data.getStringParam(DataParamName.programType).equals(TTSLocalization.LocalStrings.EPISODE.getLocalString(ttsLocalization)))
        {
            //For Recordings we pass episodes as Series
            String episodeData = EpisodicMetadataTile.renderEpisodeData(data);
            if (episodeData != null)
                buffer.append(episodeData);
        }
        else
        {
            //For actual Series
            String year = data.getStringParam(DataParamName.year);
            if(year != null){
                buffer.append(year).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));
            }

            String price = data.getStringParam(DataParamName.price);
            if (price != null)
                buffer.append(price);

            int totalEpisodes = data.getIntParam(DataParamName.totalEpisode, -1);
            if(totalEpisodes != 0 && totalEpisodes != -1){
                if(totalEpisodes == 1) {
                    buffer.append(totalEpisodes).append(" ").append(TTSLocalization.LocalStrings.EPISODE1.getLocalString(ttsLocalization));
                }
                else {
                    buffer.append(totalEpisodes).append(" ").append(TTSLocalization.LocalStrings.EPISODES.getLocalString(ttsLocalization));
                }
                buffer.append((TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization)));
            }
        }

        return buffer.toString();
    }

    @Override
    public String getCarriageReturn() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));
        return buffer.toString();
    }


    public String getViewAllTile(int position, int totalCount)
    {
        StringBuffer buffer = new StringBuffer();
        buffer.append(TTSLocalization.LocalStrings.VIEW_ALL.getLocalString(ttsLocalization));
        //buffer.append("\r\r").append(getPositionText(position,totalCount));
        return buffer.toString();
    }

    public String getNetworkTileText(DataModelEntry data){


        StringBuffer buffer = new StringBuffer();
        String channel = data.getTitle();

        if(channel != null){
            buffer.append(channel ).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));
        }

        //buffer.append(getPositionText(position,totalCount)).append(CARRIAGE_RETURN);

        return buffer.toString();
    }

    public String getPersonalityTileText(DataModelEntry data,int position, int totalCount){

        StringBuffer buffer = new StringBuffer();
        String name = data.getTitle();

        if(name != null){
            buffer.append(name).append(TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization));
        }

        //buffer.append(getPositionText(position,totalCount)).append(CARRIAGE_RETURN);

        return buffer.toString();
    }


    public String getEpisodeGuideText(XREHTMLTextResource line1PVHTMLText,XREHTMLTextResource metadataPVText)
    {
        //Remove HTML
        String returnString = com.comcast.guide.ui.Utils.removeHTML(line1PVHTMLText.getHTMLText()) + TTSLocalization.LocalStrings.CARRIAGE_RETURN.getLocalString(ttsLocalization) + com.comcast.guide.ui.Utils.removeHTML(metadataPVText.getHTMLText());

        //Replace S and Ep
        //String pattern = "(S)(\\d+)(\\s)";
        returnString = returnString.replaceAll(TTSLocalization.LocalStrings.PATTERN1.getLocalString(ttsLocalization), TTSLocalization.LocalStrings.SEASON.getLocalString(ttsLocalization));
        // pattern = "( |)(Ep)(\\d+)(\\s)";
        returnString = returnString.replaceAll(TTSLocalization.LocalStrings.PATTERN2.getLocalString(ttsLocalization), TTSLocalization.LocalStrings.EPISODE.getLocalString(ttsLocalization));

        //p to pm
        //pattern = (":(\\d\\d)p");
        return returnString.replace(TTSLocalization.LocalStrings.PATTERN3.getLocalString(ttsLocalization), TTSLocalization.LocalStrings.PM.getLocalString(ttsLocalization));
    }

	
	----------------------
	
	package com.comcast.xre.services;

import com.comcast.xre.XREView;
import com.comcast.xre.resource.view.XREHTMLTextResource;
import com.comcast.xre.resource.view.XRETextResource;
import com.comcast.xre.toolkit.Choice;
import com.comcast.xre.toolkit.ChoosableText;
import com.comcast.xre.toolkit.PanelView;
import com.comcast.xre.toolkit.ToolkitApplication;
import com.comcast.xre.toolkit.presentation.model.AbstractModel;
import com.comcast.xre.toolkit.presentation.model.DataModelEntry;
import com.comcast.xre.toolkit.presentation.model.ModuleModel;
import com.comcast.xre.toolkit.presentation.view.SimpleModuleView;
import com.comcast.xre.toolkit.scroll.BasicVirtualizer;

import java.util.List;
import java.util.ResourceBundle;

public interface ITextToSpeechController extends IService{
    void setActivated(boolean activated);

    String getGuideMainMenuModeText(ToolkitApplication app);

    String getICEListCellText(ChoosableText col2Row1Txt,ChoosableText col2Row2Txt,ChoosableText col3Row1Txt,ChoosableText col3Row2Txt);

    String getCommandIconListCellText(ChoosableText txt);

    String getFilmstripText(PanelView titlePanel);

    String getPanelViewText(XRETextResource txtResource);

    String getSpeakableTextPanelText();

    String getHeaderModuleText(List<String> pathStack);

    String getCommandListCellText(ChoosableText txt);

    String getSeriesText(DataModelEntry data,int position, int totalCount);

    String getCarriageReturn();

    String getMovieTileText(DataModelEntry data,int position, int totalCount);

    String getEpisodeGuideText(XREHTMLTextResource line1PVHTMLText,XREHTMLTextResource metadataPVText);

    String getNetworkTileText(DataModelEntry data);

    String getPersonalityTileText(DataModelEntry data,int position, int totalCount);

    String getViewAllTile(int position, int totalCount);

    String getPositionText(int position, int totalCount);

    String getRowControlPresentationText(AbstractModel model, Choice<ModuleModel> choice, List<SimpleModuleView> modules, BasicVirtualizer<ModuleModel> virtualizer);

    String getAbstractTileText();

    String getActionBarText();

    String getEpisodicMetadataTile(int position, int totalCount);

    String getStationTile(int position, int totalCount);

    ResourceBundle getTTSLocalization();

    boolean getActivated();

    void setEnabled(boolean enabled);

    boolean getEnabled();

    void speak(String speakText);

    void speak(String speakText, boolean appendToLast);

    void stop();

    void interrupt();

    void speakView(XREView view);

    String recursiveText(XREView view);

    String genericRecursiveText(XREView view);

    void listPresented(String prologue, List<String> list, int chosenItem, TextToSpeech.Epilogue epilogue);

    void listItemChanged(List<String> list, int chosenItem);

    void dataPresented(List<String> data);

    String create2DListText(List<String> list, int chosenItem, int chosenRow, int totalRows);

    String createListText(List<String> list, int chosenItem);

    String createEpilogueText(TextToSpeech.Epilogue epilogue);

    String getAccessibilityText();

    @Override
    String getName();

    String getAccessilityText();


    class TextToSpeech {

        public enum Epilogue
        {
            MOVE_INSTRUCTION,
            VERTICAL_INSTRUCTION,
            HORIZONTAL_INSTRUCTION,
            X2_HORIZONTAL_INSTRUCTION;
        }
    }
}

	

}