package com.chronoblocks.ui.panels;

import com.chronoblocks.ui.timeline.commands.*;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.ui.timeline.dialog.*;
import com.chronoblocks.ui.timeline.interaction.*;
import com.chronoblocks.ui.timeline.menu.TimelineContextMenu;
import com.chronoblocks.ui.timeline.menu.TimelineContextMenuFactory;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.TimelineViewportState;
import com.chronoblocks.ui.timeline.model.TimelineMarker;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import com.chronoblocks.ui.timeline.model.weather.WeatherKeyframe;
import com.chronoblocks.ui.timeline.render.TimelineRenderer;
import com.chronoblocks.ui.timeline.state.*;
import com.chronoblocks.ui.timeline.ui.TimelineControlBar;
import com.chronoblocks.ui.timeline.shortcuts.TimelineShortcutManager;
import com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.ImDrawList;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiInputTextFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * ChronoBlocks 涓撲笟绾ф椂闂磋酱闈㈡澘锛堟灦鏋勯噸鏋勭増 v8.0锛?
 * <p>
 * 鍩轰簬瀹屽叏瑙ｈ€︾殑 MVC 鏋舵瀯璁捐锛?
 * <ul>
 *   <li>鉁?Model: {@link TimelineModel} - 绾暟鎹ā鍨?/li>
 *   <li>鉁?View: {@link TimelineRenderer} - 绾覆鏌撻€昏緫</li>
 *   <li>鉁?Controller: {@link TimelinePanel} - 涓ぎ鍗忚皟鍣?/li>
 *   <li>鉁?Command Pattern: 瀹屾暣鐨勬挙閿€/閲嶅仛</li>
 *   <li>鉁?Interaction Handler: 涓撹亴浜や簰澶勭悊鍣?/li>
 *   <li>鉁?Control Bar: 鐙珛鐨勫伐鍏锋爮缁勪欢</li>
 *   <li>鉁?Context Menu Factory: 鑿滃崟宸ュ巶妯″紡</li>
 *   <li>鉁?Drag & Drop Handler: 涓撻棬鐨勬嫋鏀惧鐞嗗櫒</li>
 *   <li>鉁?Navigation Handler: 涓撻棬鐨勫鑸?缂╂斁澶勭悊鍣?/li>
 *   <li>鉁?Track Controller: 涓撻棬鐨勮建閬撶鐞嗘帶鍒跺櫒</li>
 * </ul>
 * </p>
 * 
 * <p>
 * 鎷嗗垎鍘熷垯锛?
 * <ul>
 *   <li>{@link TimelineDragAndDropHandler} - 澶勭悊鎷栨斁閫昏緫鍜屽潗鏍囪绠?/li>
 *   <li>{@link TimelineNavigationHandler} - 澶勭悊瑙嗗浘缂╂斁銆佹粴鍔ㄥ拰骞崇Щ</li>
 *   <li>{@link TimelineTrackController} - 澶勭悊杞ㄩ亾鐨?CRUD 鎿嶄綔鍜岃彍鍗曟覆鏌?/li>
 * </ul>
 * </p>
 * 
 * @author ChronoBlocks Team
 * @version 8.0 - 鑱岃矗鎷嗗垎浼樺寲
 * @since 1.0
 */
public class TimelinePanel extends AbstractPanel 
        implements TimelineInteractionHandler.InteractionCallbacks,
                   TimelineControlBar.Callbacks,
                   ContainerManager.ContainerListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelinePanel.class);
    
    // ========== UI 甯冨眬甯搁噺锛坴9.0 浼樺寲锛氭彁鍙栭瓟娉曟暟瀛楋級==========
    /** 杞ㄩ亾鍚嶇О缂栬緫妗嗙殑 X 鍋忕Щ閲忥紙鐩稿浜庣獥鍙ｅ乏渚э級 */
    private static final float TRACK_EDIT_OFFSET_X = 28f;
    /** 杞ㄩ亾鍚嶇О缂栬緫妗嗙殑 Y 鍋忕Щ閲忥紙鐩稿浜庤建閬撻《閮級 */
    private static final float TRACK_EDIT_OFFSET_Y = 6f;
    /** 杞ㄩ亾鍚嶇О缂栬緫妗嗗搴﹂渶瑕佸噺鍘荤殑鍊硷紙涓哄彸渚ф帶浠剁暀鍑虹┖闂达級 */
    private static final float TRACK_EDIT_WIDTH_SUBTRACT = 65f;
    /** 杞ㄩ亾鍚嶇О缂栬緫妗嗙殑鏈€灏忓搴︼紙闃叉杩囧皬鏃犳硶杈撳叆锛?*/
    private static final float TRACK_EDIT_MIN_WIDTH = 20f;
    /** 鍒诲害灏轰氦浜掔殑鏈€灏忓搴︼紙灏忎簬姝ゅ€间笉澶勭悊浜や簰锛岄伩鍏嶅竷灞€寮傚父锛?*/
    private static final float RULER_INTERACTION_MIN_WIDTH = 10f;
    /** 铏氭嫙婊氬姩鏉″簳閮ㄥ～鍏咃紙鍍忕礌锛夛紝鐢ㄤ簬鏀瑰杽婊氬姩浣撻獙 */
    private static final float DUMMY_SCROLL_PADDING = 50.0f;
    
    // ========== 鏍稿績缁勪欢锛堟灦鏋勪紭鍖栵級==========
    private final TimelineModel model;
    private final TimelineViewportState viewportState;
    private final TimelineRenderer renderer;
    private final TimelineCommandHistory commandHistory;
    private final TimelineInteractionManager interactionManager;
    private final TimelineInteractionHandler interactionHandler;
    private final TimelineControlBar controlBar;
    private final TimelineShortcutManager shortcutManager;
    
    // ========== 鏂扮殑鎷嗗垎缁勪欢 (Controllers/Handlers) ==========
    private final TimelineDragAndDropHandler dragDropHandler;
    private final TimelineNavigationHandler navigationHandler;
    private final TimelineTrackController trackController;
    
    // ========== UI 鐘舵€侊紙灏佽浼樺寲锛?=========
    private final ClipboardManager clipboard = ClipboardManager.getInstance();
    private final AutoSaveManager autoSave;
    private int dropTargetTrackIndex = -1;
    
    // ========== 鎬ц兘浼樺寲锛氬熀浜庢椂闂寸殑璁℃椂鍣?==========
    // 浣跨敤鏃堕棿鎴充唬鏇垮抚璁℃暟锛岄伩鍏嶅彈 FPS 娉㈠姩褰卞搷
    private long lastAutoSaveTime = 0;
    
    // ========== 甯搁噺瀹氫箟 ==========
    private static final int MAX_HISTORY_SIZE = 100;
    private static final float KEYFRAME_TOLERANCE = 0.1f;
    @SuppressWarnings("unused")
    private static final float CLIP_DUPLICATE_OFFSET = 0.5f; // 棰勭暀锛氱墖娈靛鍒舵椂鐨勫亸绉婚噺
    @SuppressWarnings("unused")
    private static final float FIXED_DELTA_TIME = 1.0f / 60.0f; // 棰勭暀锛?0 FPS 鍥哄畾鏃堕棿姝ラ暱锛堝彲鍦╱pdatePlayback涓娇鐢級
    private static final long AUTO_SAVE_INTERVAL_MS = 5000; // 5绉掞紙鍩轰簬鏃堕棿锛屼笉鍙桭PS褰卞搷锛?

    public TimelinePanel() {
        super();
        
        // 1. 鍒濆鍖栨牳蹇冪粍浠?
        this.viewportState = new TimelineViewportState();
        this.model = new TimelineModel(viewportState);
        this.renderer = new TimelineRenderer(model, viewportState);
        this.commandHistory = new TimelineCommandHistory();
        this.interactionManager = new TimelineInteractionManager();
        this.interactionHandler = new TimelineInteractionHandler(model, interactionManager, commandHistory, renderer);
        
        // 閰嶇疆鍘嗗彶绠＄悊
        commandHistory.setMaxHistorySize(MAX_HISTORY_SIZE);
        // 璁剧疆妯″瀷寮曠敤锛屼娇鍛戒护鍘嗗彶鑷姩瑙﹀彂妯″瀷鏇存柊
        commandHistory.setModel(model);
        
        // 2. 鍒濆鍖栨媶鍒嗗嚭鏉ョ殑澶勭悊鍣?
        this.dragDropHandler = new TimelineDragAndDropHandler(model, this::markDataChanged);
        this.navigationHandler = new TimelineNavigationHandler(model, viewportState);
        this.trackController = new TimelineTrackController(model, commandHistory, this::markDataChanged);
        
        // 3. 鐩存帴浼犻€?this 浣滀负鍥炶皟瀵硅薄锛堢畝娲佺殑鍥炶皟娉ㄥ叆锛?
        this.interactionHandler.setCallbacks(this);
        TimelineContextMenuFactory menuFactory = TimelineContextMenuFactory.forPanel(this, model, commandHistory);
        this.interactionHandler.setMenuFactory(menuFactory);
        this.controlBar = new TimelineControlBar(model, viewportState, commandHistory, this, navigationHandler);
        
        // 4. 鍒濆鍖栬嚜鍔ㄤ繚瀛樼鐞嗗櫒
        this.autoSave = new AutoSaveManager(model);
        
        // 鍒濆鍖栬嚜鍔ㄤ繚瀛樻椂闂存埑
        this.lastAutoSaveTime = System.currentTimeMillis();
        
        // 娉ㄥ唽瀹瑰櫒-鏃堕棿绾垮叧鑱旀湇鍔?
        com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance().setTimelineModel(model);
        
        // 娉ㄥ唽鐗囨鏂瑰潡鎺у埗鍣紝鏍规嵁鎾斁澶翠綅缃帶鍒剁墖娈垫柟鍧楁樉绀?
        com.chronoblocks.ui.timeline.playback.TimelineClipBlockController.getInstance().setTimelineModel(model);
        TimelineCameraController.getInstance().setTimelineModel(model);
        
        // 娉ㄥ唽瀹瑰櫒鐩戝惉鍣紝鐢ㄤ簬鍚屾瀹瑰櫒鍚嶇О鍙樺寲鍒版椂闂寸嚎鐗囨
        Objects.requireNonNull(ContainerManager.getInstance()).addListener(this);
        
        // 鍒濆鍖栧璇濇
        ClipRenameDialog.initialize(model, commandHistory);
        ClipLengthDialog.initialize(commandHistory, model);
        ClipBindingDialog.initialize(model, commandHistory, this::markDataChanged);
        TrackRenameDialog.initialize(model, commandHistory, this::markDataChanged);
        
        // 鍒濆鍖栧揩鎹烽敭绠＄悊鍣?
        this.shortcutManager = new TimelineShortcutManager(
            model, commandHistory, interactionManager,
            new TimelineShortcutManager.Callbacks() {
                @Override
                public void cutSelectedClip() {
                    TimelinePanel.this.cutSelectedClip();
                }
                
                @Override
                public void copySelectedClip() {
                    TimelinePanel.this.copySelectedClip();
                }
                
                @Override
                public void pasteClip() {
                    TimelinePanel.this.pasteClip();
                }
                
                @Override
                public void deleteSelectedClip() {
                    TimelinePanel.this.deleteSelectedClip();
                }
            }
        );
    }

    @Override
    public String getPanelName() {
        return "Timeline";
    }
    
    @Override
    public String getWindowName() {
        return "Timeline###TimelinePanel";
    }
    
    @Override
    protected int getWindowFlags() {
        return ImGuiWindowFlags.NoCollapse;
    }
    
    @Override
    protected void doRender() {
        // 1. 鏇存柊鎾斁鐘舵€侊紙姣忓抚璋冪敤锛?        updatePlayback();
        
        // 2. 鏍规嵁鎾斁澶翠綅缃洿鏂扮墖娈垫柟鍧楁樉绀虹姸鎬侊紙姣忓抚璋冪敤锛?        com.chronoblocks.ui.timeline.playback.TimelineClipBlockController.getInstance().update();
        TimelineCameraController.getInstance().update();
        
        // 3. 澶勭悊蹇嵎閿?        shortcutManager.handle();
        
        // 3. 娓叉煋鎺у埗鏍忥紙濮旀墭缁欎笓闂ㄧ殑缁勪欢锛?
        renderControlBar();
        
        // 鎺у埗鏍忎笌鍒嗗壊绾夸箣闂寸殑闂磋窛锛堢瓑浜庢寜閽笌鎸夐挳涔嬮棿鐨勯棿璺濓紝4鍍忕礌锛?
        // 娉ㄦ剰锛氭帶鍒舵爮璁剧疆浜?ItemSpacing=4px锛屾帶鍒舵爮缁撴潫鍚庝細鑷姩娣诲姞 4px 闂磋窛
        // 鎵€浠ユ帶鍒舵爮鍒板垎鍓茬嚎涔嬮棿宸茬粡鏈?4px 闂磋窛浜嗭紝鎴戜滑涓嶉渶瑕佸啀鎵嬪姩娣诲姞
        // 浣嗛渶瑕佷复鏃舵竻闄?ItemSpacing 浠ラ伩鍏?separator 涔熻嚜鍔ㄦ坊鍔犻棿璺?
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);
        ImGui.separator();
        // 鍒嗗壊绾夸笌鏃堕棿鍒诲害鏉′箣闂寸殑闂磋窛锛堢瓑浜庢寜閽笌鎸夐挳涔嬮棿鐨勯棿璺濓紝4鍍忕礌锛?
        // 浣跨敤鍥哄畾鐨勫儚绱犻棿璺濓紝纭繚 Header 鍜?Separator 涓嶄細澶尋
        ImGui.dummy(0f, UITheme.Spacing.ELEMENT_SPACING); // 4px: 鍒嗗壊绾垮埌鏃堕棿鍒诲害鐨勯棿璺?
        ImGui.popStyleVar(); // 鎭㈠ ItemSpacing
        
        // 4. 娓叉煋鏃堕棿绾跨敾甯?
        renderTimelineCanvas();
        
        // 5. 娓叉煋鏂板缓杞ㄩ亾鑿滃崟锛堝鎵樼粰 TrackController锛?
        trackController.renderAddTrackMenu();
        
        // 6. 涓婁笅鏂囪彍鍗曠幇鍦ㄥ湪瀛愮獥鍙ｅ唴娓叉煋锛堣 renderTimelineCanvas 鏂规硶锛?
        // 杩欐牱鍙互纭繚 popup 鍦ㄦ纭殑绐楀彛涓婁笅鏂囦腑鏄剧ず
        
        // 7. 鑷姩淇濆瓨锛堟€ц兘浼樺寲锛氬熀浜庢椂闂寸殑妫€鏌ワ紝涓嶅彈FPS娉㈠姩褰卞搷锛?
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAutoSaveTime >= AUTO_SAVE_INTERVAL_MS) {
            autoSave.tick();
            lastAutoSaveTime = currentTime;
        }
    }
    
    /**
     * 鏇存柊鎾斁鐘舵€侊紙姣忓抚璋冪敤锛?
     * 
     * <p>鎬ц兘浼樺寲锛?
     * <ul>
     *   <li>鏃╂湡杩斿洖锛氫笉鎾斁鏃剁洿鎺ヨ繑鍥?/li>
     *   <li>鍥哄畾鏃堕棿姝ラ暱锛氫娇鐢ㄥ浐瀹氭椂闂存闀块伩鍏嶅抚鐜囨尝鍔ㄥ鑷存椂闂翠笉鍑?/li>
     * </ul>
     * 
     * <p>鑷姩璺熼殢锛?
     * <ul>
     *   <li>鎾斁鏃惰嚜鍔ㄨ皟鏁磋鍥撅紝纭繚鎾斁澶村缁堝湪鍙鍖哄煙鍐?/li>
     *   <li>鍙傝€?Premiere Pro 绛変笓涓氳棰戠紪杈戣蒋浠剁殑琛屼负</li>
     * </ul>
     */
    private void updatePlayback() {
        // 鏃╂湡杩斿洖锛氫笉鎾斁鏃剁洿鎺ヨ繑鍥烇紝閬垮厤涓嶅繀瑕佺殑璁＄畻
        if (!model.isPlaying()) {
            return;
        }
        
        // 浣跨敤鍥哄畾鏃堕棿姝ラ暱锛岄伩鍏嶅抚鐜囨尝鍔ㄥ鑷存椂闂翠笉鍑?
        // 瀹為檯搴旂敤涓彲浠ユ牴鎹渶瑕侀€夋嫨浣跨敤鍥哄畾鏃堕棿姝ラ暱鎴栧疄鏃舵椂闂?
        float deltaTime = ImGui.getIO().getDeltaTime();
        // 鍙€夛細浣跨敤鍥哄畾鏃堕棿姝ラ暱
        // float deltaTime = FIXED_DELTA_TIME;
        
        // 鏇存柊褰撳墠鏃堕棿
        float newTime = model.getCurrentTime() + deltaTime;
        float duration = model.getDuration();
        
        // 澶勭悊瓒呭嚭鑼冨洿
        if (newTime > duration) {
            newTime = duration; // 鍋滃湪缁堢偣
            // 鈽?浼樺寲锛氬彧鍦ㄧ姸鎬佺湡姝ｆ敼鍙樻椂璁板綍鏃ュ織锛岄伩鍏嶉绻佽Е鍙戞椂鍒峰睆
            // 妫€鏌ユ槸鍚︿粠鎾斁鐘舵€佸彉涓哄仠姝㈢姸鎬?
            boolean wasPlaying = model.isPlaying();
            model.setPlaying(false);
            if (wasPlaying && LOGGER.isTraceEnabled()) {
                // 浣跨敤 trace 绾у埆锛屽洜涓鸿繖鏄珮棰戜簨浠讹紝debug 绾у埆鍙兘杩囦簬棰戠箒
                LOGGER.trace("[TimelinePanel] 鎾斁缁撴潫锛屽仠鍦ㄧ粓鐐?(鏃堕棿: {:.2f}s)", newTime);
            }
        }
        
        model.setCurrentTime(newTime);
        
        // 鈽?鑷姩璺熼殢锛氱‘淇濇挱鏀惧ご濮嬬粓鍦ㄥ彲瑙佸尯鍩熷唴
        // 鍦ㄦ挱鏀炬椂锛屽鏋滄挱鏀惧ご瓒呭嚭鍙鑼冨洿锛岃嚜鍔ㄨ皟鏁?scrollX 浣挎挱鏀惧ご淇濇寔鍦ㄥ彲瑙佸尯鍩熷唴
        // 杩欑‘淇濇挱鏀炬椂娓告爣濮嬬粓鍙锛岀敤鎴峰彲浠ラ殢鏃剁湅鍒板綋鍓嶆挱鏀句綅缃?
        ensurePlayheadVisible();
    }
    
    /**
     * 缂撳瓨鐨勫彲瑙嗗尯鍩熷弬鏁帮紙鐢ㄤ簬鑷姩璺熼殢鍔熻兘锛?
     * 杩欎簺鍊煎湪 renderTimelineCanvas() 涓洿鏂帮紝鍦?updatePlayback() 涓娇鐢?
     */
    private float cachedContentX = 0f;
    private float cachedContentW = 0f;
    private static final float PLAYHEAD_MARGIN = 50f; // 鎾斁澶磋窛绂诲彲瑙佸尯鍩熻竟缂樼殑鏈€灏忚窛绂伙紙鍍忕礌锛?
    
    /**
     * 纭繚鎾斁澶村缁堝湪鍙鍖哄煙鍐咃紙鑷姩璺熼殢鍔熻兘锛?
     * 
     * <p>绫讳技 Premiere Pro 绛変笓涓氳棰戠紪杈戣蒋浠讹紝鎾斁鏃舵父鏍囨案杩滀笉浼氳窇鍑哄睆骞曞銆?
     * 濡傛灉鎾斁澶磋秴鍑哄彲瑙佽寖鍥达紝鑷姩璋冩暣 scrollX 浣挎挱鏀惧ご淇濇寔鍦ㄥ彲瑙佸尯鍩熷唴銆?
     * 
     * <p>v9.0 浼樺寲锛氶槻姝笌鐢ㄦ埛鎵嬪姩瀵艰埅鍐茬獊
     * <ul>
     *   <li>濡傛灉鐢ㄦ埛姝ｅ湪鎷栨嫿婊氬姩鏉°€佸钩绉昏鍥炬垨杩涜鍏朵粬浜や簰锛屾殏鍋滆嚜鍔ㄨ窡闅?/li>
     *   <li>閬垮厤鐢ㄦ埛鍦ㄦ煡鐪嬪叾浠栧尯鍩熸椂琚己鍒舵媺鍥炴挱鏀惧ご浣嶇疆锛屽鑷寸敾闈㈡姈鍔ㄦ垨鎿嶄綔澶辨晥</li>
     * </ul>
     */
    private void ensurePlayheadVisible() {
        // 鍙湁鍦ㄦ湁鏁堝尯鍩熸椂鎵嶆墽琛岃嚜鍔ㄨ窡闅?
        if (cachedContentW <= 0) {
            return;
        }
        
        // 鈽?v9.0 浼樺寲锛氶槻姝笌鐢ㄦ埛鎵嬪姩瀵艰埅鍐茬獊
        // 濡傛灉鐢ㄦ埛鎸変笅浜嗛紶鏍囷紙鍙兘鍦ㄦ嫋鎷芥粴鍔ㄦ潯銆佸钩绉昏鍥炬垨杩涜鍏朵粬浜や簰锛夛紝鏆傛椂绂佺敤鑷姩璺熼殢
        // 杩欐牱鐢ㄦ埛鍙互鍦ㄦ挱鏀捐繃绋嬩腑鑷敱鏌ョ湅鍏朵粬鍖哄煙锛屼笉浼氳寮哄埗鎷夊洖鎾斁澶翠綅缃?
        if (ImGui.isMouseDown(ImGuiMouseButton.Left) || 
            ImGui.isMouseDown(ImGuiMouseButton.Middle) ||
            ImGui.isMouseDown(ImGuiMouseButton.Right)) {
            return;
        }
        
        // 妫€鏌ユ槸鍚︽湁浠讳綍娲昏穬鐨?ImGui 鎺т欢锛堜緥濡傛粦鍧椼€佽緭鍏ユ绛夛級
        // 濡傛灉鐢ㄦ埛姝ｅ湪涓?UI 浜や簰锛屾殏鍋滆嚜鍔ㄨ窡闅?
        if (ImGui.isAnyItemActive() || ImGui.isAnyItemHovered()) {
            return;
        }
        // 杩欐牱鐢ㄦ埛鍙互鑷敱鏌ョ湅鏃堕棿绾跨殑鍏朵粬鍖哄煙锛岃€屼笉浼氳寮哄埗鎷夊洖鎾斁澶翠綅缃?
        if (ImGui.isMouseDown(ImGuiMouseButton.Left) || 
            ImGui.isMouseDown(ImGuiMouseButton.Middle) ||
            ImGui.isMouseDown(ImGuiMouseButton.Right)) {
            return;
        }
        
        // 濮旀墭缁?NavigationHandler 澶勭悊
        navigationHandler.ensurePlayheadVisible(cachedContentX, cachedContentW, PLAYHEAD_MARGIN);
    }

    /**
     * 鏍囪鏁版嵁宸叉敼鍙橈紙濮旀墭缁?AutoSaveManager锛?     */
    private void markDataChanged() {
        autoSave.markDirty();
    }

    /**
     * 閲嶇疆鏃堕棿绾夸笌瀹瑰櫒鍏宠仈锛堜緵鑿滃崟鈥滄柊寤哄姩鐢烩€濊皟鐢級銆?     */
    public void resetWorkspace(boolean clearContainers) {
        model.resetToEmpty();
        if (clearContainers) {
            Objects.requireNonNull(ContainerManager.getInstance()).clear();
        }
        autoSave.markDirty();
    }
    
    /**
     * 娓叉煋鎺у埗鏍忥紙濮旀墭缁欎笓闂ㄧ殑缁勪欢锛?
     */
    private void renderControlBar() {
        controlBar.render();
    }
    
    /**
     * 瀹夊叏鍦版帹鍏ヨ鍓尯鍩燂紙闃叉杈圭紭鎯呭喌宕╂簝锛?
     * 
     * <p>鎬ц兘浼樺寲锛氬鏋滅敤鎴峰皢绐楀彛璋冩暣寰楅潪甯稿皬锛堜緥濡傞珮搴﹀皬浜?Header 楂樺害锛夛紝
     * trackViewportHeight 鍙兘鎺ヨ繎 0銆傝櫧鐒跺仛浜?Math.max(10f, ...) 鐨勪繚鎶わ紝
     * 浣?pushClipRect 濡傛灉鎺ユ敹鍒?min > max 鐨勫潗鏍囷紙渚嬪鍦ㄦ瀬绔殑甯冨眬璁＄畻閿欒
     * 鎴栫獥鍙ｆ渶灏忓寲鐬棿锛夛紝浼氬鑷?ImGui 鍐呴儴鏂█澶辫触鎴栨覆鏌撳紓甯搞€?
     * 
     * @param drawList ImGui 缁樺埗鍒楄〃
     * @param x1 瑁佸壀鍖哄煙宸︿笂瑙?X
     * @param y1 瑁佸壀鍖哄煙宸︿笂瑙?Y
     * @param x2 瑁佸壀鍖哄煙鍙充笅瑙?X
     * @param y2 瑁佸壀鍖哄煙鍙充笅瑙?Y
     */
    private void pushClipRectSafe(ImDrawList drawList, float x1, float y1, float x2, float y2) {
        if (x2 <= x1 || y2 <= y1) {
            // 濡傛灉鍖哄煙鏃犳晥锛屾帹鍏ヤ竴涓┖鐨勮鍓尯锛堣鍓帀鎵€鏈夊唴瀹癸級锛岄槻姝?ImGui 鎶ラ敊
            // 鎴栬€呮帹鍏ヤ竴涓?1x1 鐨勫畨鍏ㄥ尯鍩?
            drawList.pushClipRect(x1, y1, x1 + 1f, y1 + 1f);
        } else {
            drawList.pushClipRect(x1, y1, x2, y2);
        }
    }
    
    /**
     * 鏂扮増鏃堕棿绾跨敾甯冿紙v8.0锛夛細
     * - 涓婃柟锛氭椂闂存爣灏?+ 杞ㄩ亾鏍囬鍖哄煙锛堝浐瀹氾級
     * - 涓嬫柟锛氳建閬撳尯鍩熷瓙绐楀彛锛堝瀭鐩存粴鍔級
     * - Ctrl + 婊氳疆锛氱缉鏀撅紙鍥寸粫榧犳爣锛?
     * - Shift + 婊氳疆锛氭按骞虫粴鍔紙scrollX锛?
     */
    private void renderTimelineCanvas() {
        ImDrawList drawList = ImGui.getWindowDrawList();

        float winX = ImGui.getCursorScreenPosX();
        float winY = ImGui.getCursorScreenPosY();
        float winW = ImGui.getContentRegionAvailX();
        float availH = ImGui.getContentRegionAvailY();

        // ============================================================
        // 鈽?v9.0 淇 1锛氬厛澶勭悊甯冨眬璋冩暣閫昏緫
        // ============================================================
        // 蹇呴』鍦ㄨ绠?contentX 涔嬪墠璋冪敤锛岃繖鏍峰鏋滅敤鎴锋鍦ㄦ嫋鍔紝
        // leftLabelWidth 浼氭洿鏂帮紝鍚庣画璁＄畻鐨?contentX 鎵嶆槸褰撳墠甯ф纭殑鏁板€笺€?
        renderer.updateLeftSectionWidth(winX, winY, availH);

        float headerHeight = TimelineRenderer.getHeaderHeight();
        // 纭繚楂樺害涓嶄负璐燂紝鏈€灏忛珮搴︿负 10px 浠ラ伩鍏嶆覆鏌撻棶棰?
        float trackViewportHeight = Math.max(10f, availH - headerHeight);

        // ============================================================
        // 鈽?v9.0 淇 2锛氬熀浜庢洿鏂板悗鐨勫搴﹁绠楀潗鏍?
        // ============================================================
        // 姝ゆ椂 TimelineRenderer.getLeftLabelWidth() 杩斿洖鐨勬槸鏈€鏂板€?
        float contentX = winX + TimelineRenderer.getLeftLabelWidth();
        float contentW = winW - TimelineRenderer.getLeftLabelWidth();
        float trackRegionTop = winY + headerHeight;

        // 鈽?缂撳瓨鍙鍖哄煙鍙傛暟锛堢敤浜庤嚜鍔ㄨ窡闅忓姛鑳斤級
        // 纭繚浣跨敤鐨勬槸鏈€鏂板€?
        cachedContentX = contentX;
        cachedContentW = contentW;

        // 1) 鍛婅瘔 renderer 褰撳墠瑙嗗彛鍙傛暟锛堢敤浜庡潗鏍囪浆鎹級
        // 杩欎竴姝ラ潪甯搁噸瑕侊紝瀹冧細灏?contentX 鍚屾缁?Renderer 鍐呴儴鐨?Viewport 瀵硅薄
        renderer.beginFrame(winX, winY, winW, availH);
        
        // 鈽?浼樺寲 A锛氭樉寮忚鍓?Header 鍖哄煙锛岄槻姝㈡爣灏虹粯鍒跺埌澶栭潰
        pushClipRectSafe(drawList, winX, winY, winX + winW, winY + headerHeight);
        renderer.renderTimelineHeader(drawList, winX, winY, winW);
        drawList.popClipRect();

        // 2.5) 澶勭悊鍒诲害灏哄尯鍩熶氦浜掞紙鐐瑰嚮璺宠浆鎾斁澶达紝鎷栧姩鎾斁澶达級
        handleRulerInteractions(winX, winY, headerHeight, contentX, contentW);

        // 3) 澶勭悊婊氳疆锛氱缉鏀?/ 姘村钩婊氬姩锛堝鎵樼粰 NavigationHandler锛?
        navigationHandler.handleMouseWheel(winX, winY, winW, headerHeight, trackViewportHeight);

        if (trackViewportHeight <= 0f) {
            // 绌洪珮搴﹀氨涓嶆覆鏌撹建閬撲簡
            ImGui.setCursorScreenPos(winX, winY + headerHeight);
            return;
        }

        // 4) 璁＄畻杞ㄩ亾鎬婚珮搴︼紙鐢ㄤ簬婊氬姩鏉★級
        int trackCount = model.getTrackCount();
        float trackHeight = TimelineRenderer.getTrackHeight();
        float trackGap = TimelineRenderer.getTrackGap();
        float rowHeight = trackHeight + trackGap;
        float totalTracksHeight = trackCount > 0 ? 
            (trackCount * trackHeight + (trackCount - 1) * trackGap) : 0f;
        
        // 杞ㄩ亾鍖哄煙瀛愮獥鍙ｏ細鏀寔鍨傜洿婊氬姩
        // 濡傛灉杞ㄩ亾鎬婚珮搴﹁秴杩囪鍙ｉ珮搴︼紝ImGui 浼氳嚜鍔ㄦ樉绀哄瀭鐩存粴鍔ㄦ潯
        ImGui.setCursorScreenPos(winX, trackRegionTop);
        
        // 鈽?浼樺寲 B锛氱Щ闄?HorizontalScrollbar 鏍囧織
        // 鎴戜滑鍙渶瑕佸瀭鐩存粴鍔ㄦ潯锛屾按骞虫柟鍚戠敱 Virtual ScrollX 鎺у埗
        // 浣跨敤 None 鏍囧織锛孖mGui 浼氳嚜鍔ㄥ鐞嗗瀭鐩存粴鍔ㄦ潯锛堝綋鍐呭瓒呭嚭鏃讹級
        ImGui.beginChild("##TimelineTrackRegion",
                winW, trackViewportHeight,
                false,
                ImGuiWindowFlags.None); // 绉婚櫎 HorizontalScrollbar锛孖mGui浼氳嚜鍔ㄥ鐞嗗瀭鐩存粴鍔?
        
        // 璁＄畻铏氭嫙楂樺害锛屾拺寮€鍨傜洿婊氬姩鏉?
        // 鈽?v9.0 浼樺寲锛氫娇鐢ㄥ父閲忔浛鎹㈤瓟娉曟暟瀛楋紝鏀瑰杽婊氬姩浣撻獙
        float totalTracksHeightWithPadding = totalTracksHeight + DUMMY_SCROLL_PADDING;
        
        if (totalTracksHeightWithPadding > trackViewportHeight) {
            // 鍦ㄥ唴瀹圭粨鏉熶綅缃垱寤鸿櫄鎷熸帶浠讹紝鎾戝紑婊氬姩鏉?
            ImGui.setCursorPos(0, totalTracksHeightWithPadding);
            ImGui.dummy(1.0f, 1.0f); // 鎾戝紑婊氬姩鏉?
            // 褰掍綅鍒板紑濮嬩綅缃?
            ImGui.setCursorPos(0, 0);
        }

        float scrollY = ImGui.getScrollY();
        viewportState.setScrollY(scrollY);

        // 鈽?浼樺寲 A锛氭樉寮忚鍓建閬撳尯鍩?
        // 瑁佸壀鏁翠釜杞ㄩ亾鍖哄煙锛堥槻姝㈠唴瀹瑰湪婊氬姩鏃舵孩鍑哄瓙绐楀彛杈圭晫锛?
        pushClipRectSafe(drawList, winX, trackRegionTop, winX + winW, trackRegionTop + trackViewportHeight);
        
        // 鍦ㄤ富绐楀彛鍧愭爣绯婚噷鐢伙紙鎵€浠ヤ紶 winX / trackRegionTop锛夛紝scrollY 浠?viewportState 鍚屾
        // 娉ㄦ剰锛歳enderTrackArea 浼氭覆鏌撴暣涓尯鍩燂紙鍖呮嫭宸︿晶鏍囩鍜屽彸渚у唴瀹癸級
        // 浣嗙墖娈垫覆鏌撳湪 renderClip 涓凡缁忚闄愬埗鍦ㄥ唴瀹瑰尯鍩熷唴锛堥€氳繃 clampedX1/clampedX2锛?
        renderer.renderTrackArea(drawList, winX, trackRegionTop, winW, trackViewportHeight);
        
        // 6) Drop 楂樹寒锛堝湪瑁佸壀鍖哄煙鍐咃級
        if (dropTargetTrackIndex >= 0) {
            // 浣跨敤宸茶绠楃殑 rowHeight
            float y = trackRegionTop - scrollY + dropTargetTrackIndex * rowHeight;
            renderer.renderDropHighlight(drawList, contentX, y, contentW);
        }
        
        // 鎭㈠杞ㄩ亾鍖哄煙瑁佸壀
        drawList.popClipRect();

        // 5) 娓叉煋鍘熶綅缂栬緫鍜屽脊绐楋紙杩欎簺鏄爣鍑?ImGui 鎺т欢锛岃嚜甯﹁鍓紝涓嶉渶瑕?pushClipRect锛?
        // 娉ㄦ剰锛欳lip 鐩稿叧鐨?Popup 搴旇鍦ㄨ鍓尯涔嬪娓叉煋锛孌ialog 浼氭柊寤?Layer锛屾墍浠ユ病闂
        // 浣?InlineEdit (InputText) 蹇呴』鍦?Child 鍐呴儴娓叉煋浠ヨ窡闅忔粴鍔?
        renderInlineEdit(winX, trackRegionTop, scrollY);
        
        // 杩欎簺 Dialog 娓叉煋閫昏緫鏄畨鍏ㄧ殑锛屽洜涓哄畠浠唴閮ㄤ娇鐢?beginPopup / beginTooltip
        TrackRenameDialog.renderPopupIfNeeded();
        ClipRenameDialog.renderIfNeeded();
        ClipBindingDialog.renderIfNeeded();
        ClipLengthDialog.renderIfNeeded();

        // 7) InvisibleButton 鐢ㄤ簬浜や簰
        // 鈽?鍏抽敭淇锛欼nvisibleButton 鍦?Child 绐楀彛涓殑瀹氫綅
        // 
        // 闂鍒嗘瀽锛?
        // - 鍦?Child 绐楀彛涓紝(0,0) 鏄唴瀹瑰尯鍩熺殑宸︿笂瑙掞紙鐩稿浜?Child 绐楀彛锛?
        // - 褰?scrollY > 0 鏃讹紝(0,0) 浼氭粴鍔ㄥ埌瑙嗗彛涓婃柟
        // - 濡傛灉浣跨敤 setCursorScreenPos锛屽潗鏍囦細涓?Child 绐楀彛鐨勫潗鏍囩郴涓嶅尮閰?
        //
        // 瑙ｅ喅鏂规锛?
        // - 浣跨敤 setCursorPos(0, 0) 鍦?Child 绐楀彛鐨勫唴瀹瑰潗鏍囩郴涓畾浣?
        // - 鍒涘缓涓€涓鐩栨暣涓唴瀹瑰尯鍩燂紙鍖呮嫭婊氬姩閮ㄥ垎锛夌殑鎸夐挳
        // - ImGui 鐨?invisibleButton 浼氳嚜鍔ㄥ鐞嗘粴鍔紝鍙鍦ㄦ纭殑 Child 绐楀彛涓婁笅鏂囦腑鍒涘缓鍗冲彲
        //
        ImGui.setCursorPos(0, 0);
        // 鍒涘缓涓€涓鐩栨暣涓唴瀹瑰尯鍩燂紙鍖呮嫭鎵€鏈夊彲鑳芥粴鍔ㄧ殑杞ㄩ亾锛夌殑鎸夐挳
        // 瀹藉害浣跨敤 winW锛岄珮搴︿娇鐢ㄦ€昏建閬撻珮搴︼紙鍔犱竴浜涜竟璺濓級锛岃繖鏍锋棤璁烘粴鍔ㄥ埌鍝噷閮借兘鎹曡幏鐐瑰嚮
        float buttonHeight = Math.max(trackViewportHeight, totalTracksHeight + 100f); // 娣诲姞杈硅窛纭繚瑕嗙洊
        ImGui.invisibleButton("##TimelineCanvas", winW, buttonHeight);
        
        // 妫€鏌?invisibleButton 鏄惁琚彸閿偣鍑伙紙鐢ㄤ簬涓婁笅鏂囪彍鍗曪級
        // 娉ㄦ剰锛歩nvisibleButton 浼氭嫤鎴彸閿簨浠讹紝鎵€浠ユ垜浠渶瑕佸湪杩欓噷妫€鏌?
        // 鈽?浼樺寲 D锛氱‘淇?InvisibleButton 鐨?ID 鍞竴鎬э紝骞跺埄鐢ㄥ叾鐘舵€?
        boolean rightClickedOnCanvas = ImGui.isItemHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Right);

        // 7.5) 璁剧疆瀹瑰櫒鎷栨斁鐩爣锛堝鎵樼粰 DragDropHandler锛?
        // 浣跨敤涓荤獥鍙ｇ殑 contentX锛堝湪瀛愮獥鍙ｄ箣鍓嶄繚瀛樼殑鍊硷級
        this.dropTargetTrackIndex = dragDropHandler.handleDragDropTarget(trackRegionTop, scrollY, contentX);

        // 8) 灏嗗嚑浣曚俊鎭紶缁欎氦浜掑鐞嗗櫒锛堝懡涓娴?/ 鎷栧姩 / 涓婁笅鏂囪彍鍗曪級
        interactionHandler.handleCanvasInteractions(contentX, trackRegionTop, contentW, trackViewportHeight, scrollY, rightClickedOnCanvas);
        
        // 8.5) 鍦ㄥ瓙绐楀彛鍐呮覆鏌撴椿鍔ㄧ殑涓婁笅鏂囪彍鍗曪紙蹇呴』鍦?endChild 涔嬪墠锛?
        // 娉ㄦ剰锛欼mGui popup 蹇呴』鍦ㄨ皟鐢?openPopup 鐨勫悓涓€绐楀彛涓婁笅鏂囦腑娓叉煋
        TimelineContextMenu menu = interactionHandler.getActiveContextMenu();
        if (menu != null) {
            menu.render();
        }

        ImGui.endChild();

        // ============================================================
        // 鈽?淇锛氬湪 EndChild 涔嬪悗娓叉煋鎾斁澶达紙v8.1 鍏抽敭淇锛?
        // ============================================================
        // 
        // 闂鍒嗘瀽锛?
        // - 瀛愮獥鍙ｏ紙Child Window锛夋湁榛樿鐨勮鍓尯鍩燂紙ClipRect锛?
        // - 濡傛灉鎾斁澶村湪瀛愮獥鍙ｅ唴閮ㄦ覆鏌擄紝鏍囧昂涓婄殑涓夎褰㈡墜鏌勪細琚瓙绐楀彛椤堕儴杈圭晫瑁佸壀鎺?
        // - 鐢ㄦ埛鍙兘鐪嬪埌杞ㄩ亾涓婄殑绾㈢嚎锛岀湅涓嶅埌鏍囧昂涓婄殑鎵嬫焺
        //
        // 瑙ｅ喅鏂规锛?
        // - 灏?renderPlayhead 绉诲埌 endChild() 涔嬪悗锛屽湪涓荤獥鍙ｅ眰绾ф覆鏌?
        // - 杩欐牱鎾斁澶翠細瑕嗙洊鍦ㄦ墍鏈夊唴瀹逛箣涓婏紝涓斾笉浼氳瀛愮獥鍙ｈ鍓?
        // - 浣跨敤鍗曠嫭鐨勮鍓尯鍩熺‘淇濇挱鏀惧ご鍙樉绀哄湪鍐呭鍖哄煙鍐?
        //
        // ============================================================
        
        // 浣跨敤涓荤獥鍙ｇ殑 DrawList锛堟鏃跺凡缁忓湪 Child 浣滅敤鍩熷锛?
        // 鈽?v9.0 鏇存柊锛氭墜鏌勭幇鍦ㄥ湪鍒诲害灏哄唴閮紙椤堕儴涓庡埢搴﹀昂椤堕儴瀵归綈锛夛紝涓嶉渶瑕佹墿灞曡鍓尯鍩?
        float trackAreaBottom = trackRegionTop + trackViewportHeight;
        
        // 璁剧疆瑁佸壀鍖哄煙锛氫粠鍒诲害灏洪《閮ㄤ竴鐩村埌杞ㄩ亾鍖哄煙搴曢儴
        // 鎵嬫焺鍦ㄥ埢搴﹀昂鍐呴儴锛屾墍浠ヤ笉闇€瑕佸悜涓婃墿灞曡鍓尯鍩?
        pushClipRectSafe(drawList, contentX, winY, contentX + contentW, trackAreaBottom);
        
        // 娉ㄦ剰鍙傛暟璋冩暣锛?
        // - contentY 浼?winY锛圚eader 椤堕儴锛夛紝浠ヤ究 renderer 璁＄畻姝ｇ‘鐨勬墜鏌勪綅缃?
        // - contentH 浼?availH锛堟€婚珮搴︼級锛屼互渚挎挱鏀惧ご绾跨敾鍒板簳閮?
        renderer.renderPlayhead(drawList, contentX, winY, contentW, availH);
        
        drawList.popClipRect();

        // 9) 鎶婂厜鏍囩Щ鍔ㄥ埌瀛愮獥鍙ｄ笅鏂癸紝閬垮厤涓嬩竴涓帶浠惰鐩?
        ImGui.setCursorScreenPos(winX, trackRegionTop + trackViewportHeight);
    }

    /**
     * 澶勭悊鍒诲害灏哄尯鍩熶氦浜掞紙鐐瑰嚮璺宠浆鎾斁澶达紝鎷栧姩鎾斁澶达級
     * 
     * <p>鍔熻兘锛?
     * <ul>
     *   <li>鍦ㄥ埢搴﹀昂涓婄偣鍑伙細鎾斁澶磋烦杞埌鐐瑰嚮浣嶇疆</li>
     *   <li>鐐瑰嚮鎾斁澶存墜鏌勶細寮€濮嬫嫋鍔ㄦ挱鏀惧ご</li>
     *   <li>鎷栧姩鎾斁澶达細瀹炴椂鏇存柊鎾斁澶翠綅缃?/li>
     * </ul>
     * 
     * @param winX 绐楀彛 X 鍧愭爣
     * @param winY 绐楀彛 Y 鍧愭爣
     * @param headerHeight Header 楂樺害锛堝寘鍚埢搴﹀昂锛?
     * @param contentX 鍐呭鍖哄煙 X 璧风偣锛堝埢搴﹀昂璧风偣锛?
     * @param contentW 鍐呭鍖哄煙瀹藉害
     */
    private void handleRulerInteractions(float winX, float winY, float headerHeight, float contentX, float contentW) {
        // 鈽?v9.0 浼樺寲锛氭坊鍔犲彲瑙佹€ф鏌ワ紝闃叉甯冨眬寮傚父
        // 濡傛灉鍐呭鍖哄煙瀹藉害杩囧皬锛堝彲鑳界獥鍙ｈ鍘嬬缉寰楀緢绐勶級锛屼笉澶勭悊浜や簰
        if (contentW < RULER_INTERACTION_MIN_WIDTH) {
            return;
        }
        
        float rulerHeight = TimelineRenderer.getRulerHeight();
        float rulerBottom = winY + rulerHeight;

        // 鍒诲害灏哄尯鍩燂紙鍙寘鍚唴瀹瑰尯鍩燂紝涓嶅寘鎷乏渚ф爣绛撅級
        float rulerX2 = contentX + contentW;
        
        // 鈽?v9.0 鏇存柊锛氭墜鏌勭幇鍦ㄥ湪鍒诲害灏哄唴閮紙椤堕儴涓庡埢搴﹀昂椤堕儴瀵归綈锛夛紝涓嶉渶瑕佹墿灞曚氦浜掑尯鍩?
        ImGui.setCursorScreenPos(contentX, winY);
        // 鈽?浼樺寲锛氭坊鍔犲疄渚嬪搱甯屽€奸伩鍏?ID 鍐茬獊锛堣櫧鐒跺綋鍓嶆槸鍗曚緥锛屼絾涓烘湭鏉ユ墿灞曢鐣欙級
        ImGui.invisibleButton("##TimelineRuler" + System.identityHashCode(this), contentW, rulerHeight);
        
        float mouseX = ImGui.getIO().getMousePosX();
        float mouseY = ImGui.getIO().getMousePosY();
        
        // 妫€鏌ラ紶鏍囨槸鍚﹀湪鍒诲害灏哄尯鍩熷唴
        boolean isInRulerArea = mouseX >= contentX && mouseX <= rulerX2 &&
                               mouseY >= winY && mouseY <= rulerBottom;
        
        if (!isInRulerArea && !ImGui.isItemHovered()) {
            return;
        }
        
        // 妫€鏌ユ槸鍚︾偣鍑诲湪鎾斁澶存墜鏌勪笂
        boolean isClickingPlayheadHandle = isPointInPlayheadHandle(mouseX, mouseY, winY, rulerHeight, contentX);
        
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            // 鏃犺鏄偣鍑绘挱鏀惧ご鎵嬫焺杩樻槸鍒诲害灏哄叾浠栦綅缃紝閮界粺涓€璋冪敤 startPlayheadDrag
            // startPlayheadDrag 鍐呴儴浼氬鐞嗕綅缃洿鏂板拰鎷栧姩寮€濮?
            interactionHandler.startPlayheadDrag(mouseX, contentX);
            
            if (isClickingPlayheadHandle) {
                LOGGER.debug("[TimelinePanel] 寮€濮嬫嫋鍔ㄦ挱鏀惧ご鎵嬫焺");
            } else {
                LOGGER.debug("[TimelinePanel] 鍒诲害灏虹偣鍑昏烦杞埌鏃堕棿: {:.2f}s", renderer.xToTime(mouseX, contentX));
            }
        }
        
        // 澶勭悊鎷栧姩锛堢敱 InteractionHandler 缁熶竴绠＄悊锛?
        // 杩欓噷涓嶉渶瑕侀澶栧鐞嗭紝鍥犱负鎷栧姩鐘舵€佸凡缁忓湪 InteractionHandler 涓鐞?
    }
    
    /**
     * 妫€鏌ョ偣鏄惁鍦ㄦ挱鏀惧ご鎵嬫焺鍖哄煙鍐咃紙鎬ц兘浼樺寲鐗堬級
     * 
     * <p>浼樺寲璇存槑锛?
     * <ul>
     *   <li>绉婚櫎浜嗘槀璐电殑閲嶅績鍧愭爣璁＄畻锛屾敼鐢ㄧ畝鍗曠殑 AABB锛堣酱瀵归綈鍖呭洿鐩掞級妫€娴?/li>
     *   <li>瀵逛簬 UI 浜や簰锛岀煩褰㈠垽瀹氭瘮绮剧‘鐨勪笁瑙掑舰鍒ゅ畾浣撻獙鏇村ソ锛堟洿澶х殑鐐瑰嚮瀹归敊鐜囷級锛屼笖鎬ц兘鏇撮珮</li>
     *   <li>閬垮厤浜嗗ぇ閲忕殑娴偣涔橀櫎杩愮畻锛屾彁鍗囦簡楂橀浜や簰鍦烘櫙涓嬬殑鎬ц兘</li>
     * </ul>
     * 
     * <p>v9.0 鏇存柊锛氭墜鏌勭幇鍦ㄥ湪鍒诲害灏哄唴閮紝椤堕儴涓庡埢搴﹀昂椤堕儴瀵归綈
     * 
     * @param mouseX 榧犳爣 X 鍧愭爣
     * @param mouseY 榧犳爣 Y 鍧愭爣
     * @param rulerTop 鍒诲害灏洪《閮?Y 鍧愭爣
     * @param rulerHeight 鍒诲害灏洪珮搴︼紙鏈娇鐢紝淇濈暀浠ヤ繚鎸?API 涓€鑷存€э級
     * @param contentX 鍐呭鍖哄煙 X 璧风偣
     * @return 鏄惁鍦ㄦ挱鏀惧ご鎵嬫焺鍐?
     */
    private boolean isPointInPlayheadHandle(float mouseX, float mouseY, float rulerTop, float rulerHeight, float contentX) {
        float currentTime = model.getCurrentTime();
        float playheadX = renderer.timeToX(currentTime, contentX);
        
        // 鈽?v9.0 鏇存柊锛氭挱鏀惧ご鎵嬫焺鍙傛暟锛堜笌娓叉煋浠ｇ爜淇濇寔涓€鑷达級
        // 鎵嬫焺鐜板湪鍙樺皬浜嗭紙6.0f锛夛紝椤堕儴涓庡埢搴﹀昂椤堕儴瀵归綈
        float handleSize = 6.0f;
        float handleHeight = handleSize * 1.5f; // 9.0f
        // 鎵嬫焺椤堕儴浣嶇疆锛氫笌鍒诲害灏洪《閮ㄥ榻愶紙涓夎褰㈢殑椤堕儴鐐瑰湪鍒诲害灏轰笂杈圭紭锛?
        float handleBottom = rulerTop + handleHeight;

        // 绠€鍗曠殑鐭╁舰鍖呭洿鐩掓娴嬶紙AABB锛?
        // X 杞磋寖鍥达細鎾斁澶翠腑蹇冨乏鍙冲悇 handleSize
        boolean insideX = (mouseX >= playheadX - handleSize) && (mouseX <= playheadX + handleSize);
        
        // Y 杞磋寖鍥达細浠庡埢搴﹀昂椤堕儴鍒版墜鏌勫簳閮紙鍦ㄥ埢搴﹀昂鍐呴儴锛?
        boolean insideY = (mouseY >= rulerTop) && (mouseY <= handleBottom);
        
        return insideX && insideY;
    }

    /**
     * 鍙屽嚮澶勭悊鍏抽敭甯ф坊鍔?鍒犻櫎
     * 
     * <p>杈圭晫妫€鏌ワ細
     * <ul>
     *   <li>妫€鏌ヨ建閬撴槸鍚﹂攣瀹?/li>
     *   <li>妫€鏌ョ墖娈垫槸鍚︽湁鏁?/li>
     * </ul>
     */
    private void handleKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX) {
        if (clip == null) {
            LOGGER.warn("[TimelinePanel] 灏濊瘯鍦ㄧ┖鐗囨涓婃坊鍔犲叧閿抚");
            return;
        }
        
        // 妫€鏌ヨ建閬撴槸鍚﹂攣瀹?
        TimelineTrack track = model.findTrackForClip(clip);
        if (track != null && track.isLocked()) {
            LOGGER.debug("[TimelinePanel] 杞ㄩ亾宸查攣瀹氾紝鏃犳硶淇敼鍏抽敭甯? {}", track.getName());
            return;
        }
        
        final float clickTime = clamp(model.screenToTime(mouseX, contentX), 
            clip.getStartTime(), clip.getEndTime());
        
        Float existingKf = clip.getKeyframeAt(clickTime, KEYFRAME_TOLERANCE);
        
        if (existingKf != null) {
            RemoveKeyframeCommand cmd = new RemoveKeyframeCommand(model, clip, existingKf);
            commandHistory.executeCommand(cmd);
            markDataChanged();
            LOGGER.debug("[TimelinePanel] 鍒犻櫎鍏抽敭甯?at {}s (鐗囨: {})", 
                String.format("%.2f", existingKf), clip.getLabel());
        } else {
            AddKeyframeCommand cmd = new AddKeyframeCommand(model, clip, clickTime);
            commandHistory.executeCommand(cmd);
            markDataChanged();
            LOGGER.debug("[TimelinePanel] 娣诲姞鍏抽敭甯?at {}s (鐗囨: {})", 
                String.format("%.2f", clickTime), clip.getLabel());
        }
    }

    /**
     * 鍘熶綅缂栬緫杞ㄩ亾鍚嶇О锛堝凡鍏煎 scrollY锛?
     */
    private void renderInlineEdit(float winX, float trackRegionTop, float scrollY) {
        if (!TrackRenameDialog.isEditingAny()) {
            return;
        }

        TimelineTrack editingTrack = TrackRenameDialog.getEditingTrack();
        if (editingTrack == null) {
            return;
        }

        int trackIndex = model.getTracks().indexOf(editingTrack);
        if (trackIndex < 0) {
            TrackRenameDialog.finishInlineEdit(false);
            return;
        }

        float rowHeight = TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap();
        float trackY = trackRegionTop - scrollY + trackIndex * rowHeight;

        // 鈽?v9.0 浼樺寲锛氫娇鐢ㄥ父閲忔浛鎹㈤瓟娉曟暟瀛楋紝鏂逛究鍚庢湡 UI 寰皟
        float nameX = winX + TRACK_EDIT_OFFSET_X;
        float nameY = trackY + TRACK_EDIT_OFFSET_Y;
        float inputWidth = TimelineRenderer.getLeftLabelWidth() - TRACK_EDIT_WIDTH_SUBTRACT;

        // 纭繚杈撳叆妗嗕笉浼氳繃灏忔棤娉曡緭鍏?
        if (inputWidth < TRACK_EDIT_MIN_WIDTH) {
            return;
        }

        ImGui.setCursorScreenPos(nameX, nameY);
        ImGui.pushItemWidth(inputWidth);

        ImGui.setKeyboardFocusHere();
        boolean enterPressed = ImGui.inputText("##TrackNameEdit", TrackRenameDialog.getInlineBuffer(),
                ImGuiInputTextFlags.EnterReturnsTrue);
        ImGui.popItemWidth();
        
        // Enter 閿‘璁?
        if (enterPressed) {
            TrackRenameDialog.finishInlineEdit(true);
        }
        
        // ESC 閿彇娑?
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            TrackRenameDialog.finishInlineEdit(false);
        }
        
        // 鐐瑰嚮鍏朵粬鍦版柟瀹屾垚缂栬緫
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && !ImGui.isItemHovered()) {
            TrackRenameDialog.finishInlineEdit(true);
        }
    }

    /**
     * 鍓垏閫変腑鐨勭墖娈碉紙蹇嵎閿級
     */
    private void cutSelectedClip() {
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;
        
        TimelineTrack track = model.findTrackForClip(selectedClip);
        if (track != null) {
            clipboard.cut(selectedClip, track, model, commandHistory);
            markDataChanged();
        }
    }
    
    /**
     * 澶嶅埗閫変腑鐨勭墖娈碉紙蹇嵎閿級
     */
    private void copySelectedClip() {
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;
        
        clipboard.copy(selectedClip);
    }
    
    /**
     * 绮樿创鐗囨锛堝揩鎹烽敭锛?
     */
    private void pasteClip() {
        clipboard.paste(model);
        markDataChanged();
    }

    /**
     * 鍒犻櫎閫変腑鐨勭墖娈?
     */
    private void deleteSelectedClip() {
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;
        
        // 鈽?杞ㄩ亾閿佸畾鍔熻兘锛氬鏋滆建閬撻攣瀹氾紝绂佹鍒犻櫎鐗囨
        TimelineTrack track = model.findTrackForClip(selectedClip);
        if (track != null && track.isLocked()) {
            LOGGER.info("[TimelinePanel] 杞ㄩ亾宸查攣瀹氾紝绂佹鍒犻櫎鐗囨 (杞ㄩ亾: {})", track.getName());
            return;
        }
        
        clipboard.deleteSelected(model, commandHistory);
        markDataChanged();
    }
    
    
    // ========== 鍥炶皟鎺ュ彛瀹炵幇锛圱imelineInteractionHandler.InteractionCallbacks锛?=========
    
    @Override
    public void onKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX) {
        handleKeyframeDoubleClick(clip, mouseX, contentX);
    }

    @Override
    public void onClipInteractionFinished(TimelineClip clip, float originalStartTime, float originalEndTime) {
        // 鈽?淇锛氫娇鐢ㄦ樉寮忎紶閫?old 鍊肩殑鏋勯€犲嚱鏁?
        // originalStartTime/originalEndTime 鏄嫋鍔ㄥ紑濮嬫椂鐨勫€硷紙鏃у€硷級
        // clip.getStartTime()/getEndTime() 鏄嫋鍔ㄧ粨鏉熸椂鐨勫€硷紙鏂板€硷級
        float currentStartTime = clip.getStartTime();
        float currentEndTime = clip.getEndTime();
        
        // 鍙湁褰撲綅缃湡姝ｆ敼鍙樻椂鎵嶅垱寤哄懡浠?
        if (Math.abs(currentStartTime - originalStartTime) > 0.001f || 
            Math.abs(currentEndTime - originalEndTime) > 0.001f) {
            // 鈽?淇锛氫娇鐢ㄦ樉寮忎紶閫?old 鍊肩殑鏋勯€犲嚱鏁帮紝閬垮厤浠庡凡淇敼鐨?clip 涓鍙栭敊璇殑鍊?
            MoveClipCommand cmd = new MoveClipCommand(
                model,
                new ClipAdapter(clip),
                originalStartTime,  // old 鍊硷細鎷栧姩寮€濮嬫椂鐨勪綅缃?
                originalEndTime,     // old 鍊硷細鎷栧姩寮€濮嬫椂鐨勪綅缃?
                currentStartTime,    // new 鍊硷細鎷栧姩缁撴潫鏃剁殑浣嶇疆锛堝綋鍓嶅€硷級
                currentEndTime       // new 鍊硷細鎷栧姩缁撴潫鏃剁殑浣嶇疆锛堝綋鍓嶅€硷級
            );
            commandHistory.executeCommand(cmd);
            model.markDurationDirty(); // 鐗囨鏃堕棿淇敼鍙兘褰卞搷鎬绘椂闀?
            markDataChanged();
            LOGGER.info("[TimelinePanel] 瀹屾垚鐗囨绉诲姩鎿嶄綔 (鐗囨: {}, 鏃堕棿: {:.2f}s-{:.2f}s -> {:.2f}s-{:.2f}s)", 
                clip.getLabel(), originalStartTime, originalEndTime, currentStartTime, currentEndTime);
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[TimelinePanel] 鐗囨浣嶇疆鏈敼鍙橈紝璺宠繃鍛戒护鍒涘缓");
            }
        }
    }

    @Override
    public void onKeyframeDragFinished(TimelineClip clip, float originalTime, float newTime) {
        // 鎺у埗鍣ㄨ礋璐ｅ垱寤哄懡浠ゅ拰绠＄悊鏁版嵁淇敼
        // 浣跨敤 Remove + Add 鍛戒护瀹炵幇鍏抽敭甯хЩ鍔?
        commandHistory.executeCommand(new RemoveKeyframeCommand(model, clip, originalTime));
        commandHistory.executeCommand(new AddKeyframeCommand(model, clip, newTime));
        markDataChanged();
        LOGGER.info("[TimelinePanel] 绉诲姩鍏抽敭甯т粠 {}s 鍒?{}s (鐗囨: {})", 
            String.format("%.2f", originalTime), String.format("%.2f", newTime), clip.getLabel());
    }

    @Override
    public void onDataChanged() {
        markDataChanged();
    }

    @Override
    public void setHoveredTrack(TimelineTrack track) {
        // 淇濈暀鎺ュ彛瀹炵幇锛屼絾涓嶅啀瀛樺偍鐘舵€侊紙鐢?InteractionHandler 鍐呴儴绠＄悊锛?
    }

    public void requestClipRename(TimelineClip clip) {
        ClipRenameDialog.open(clip);
    }

    public void requestClipContainerBinding(TimelineClip clip) {
        ClipBindingDialog.open(clip);
    }

    @Override
    public void setDropTargetTrackIndex(int index) {
        dropTargetTrackIndex = index;
    }

    @Override
    public boolean isMouseHoveringRect(float x1, float y1, float x2, float y2) {
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
    }

    @Override
    public void onAddKeyframeToTrack(TimelineTrack track) {
        if (track == null) {
            return;
        }

        float time = clamp(model.getCurrentTime(), 0f, model.getDuration());
        String timeLabel = String.format("%.2f", time);
        TrackType type = track.getType();
        boolean handled = false;

        if (type != null) {
            if (type.supportsCameraPath()) {
                CameraKeyframe keyframe = new CameraKeyframe("Camera @" + timeLabel, time);
                TimelineCameraController.getInstance().captureFromGameCamera(keyframe);
                commandHistory.executeCommand(new AddAutomationKeyframeCommand(
                    model,
                    track,
                    keyframe
                ));
                handled = true;
            } else if (type.supportsWeatherControl()) {
                commandHistory.executeCommand(new AddAutomationKeyframeCommand(
                    model,
                    track,
                    new WeatherKeyframe("Weather @" + timeLabel, time)
                ));
                handled = true;
            } else if (type.supportsMarkers() && !type.supportsClips()) {
                commandHistory.executeCommand(new AddAutomationKeyframeCommand(
                    model,
                    track,
                    new TimelineMarker("Marker @" + timeLabel, time)
                ));
                handled = true;
            }
        }

        if (!handled) {
            TimelineClip targetClip = track.findClipAtTime(time);
            if (targetClip == null) {
                TimelineClip selected = model.getSelectedClip();
                if (selected != null && model.findTrackForClip(selected) == track) {
                    targetClip = selected;
                }
            }

            if (targetClip == null) {
                LOGGER.warn("[TimelinePanel] Failed to add keyframe: track {} has no editable clip at current time", track.getName());
                return;
            }

            float clampedTime = clamp(time, targetClip.getStartTime(), targetClip.getEndTime());
            commandHistory.executeCommand(new AddKeyframeCommand(model, targetClip, clampedTime));
        }

        markDataChanged();
    }

    @Override
    public void onToggleTrackLock(TimelineTrack track) {
        if (track == null) return;
        track.setLocked(!track.isLocked());
        markDataChanged();
    }

    @Override
    public void onToggleTrackVisible(TimelineTrack track) {
        if (track == null) return;
        track.setVisible(!track.isVisible());
        markDataChanged();
    }

    @Override
    public void onContainerDropped(String containerId) {
        if (containerId == null || containerId.isEmpty()) {
            LOGGER.warn("[TimelinePanel] 瀹瑰櫒ID涓虹┖锛屾嫆缁濇嫋鏀?");
            return;
        }
        
        try {
            // 1. 楠岃瘉骞惰В鏋愬鍣↖D
            UUID containerUuid;
            try {
                containerUuid = UUID.fromString(containerId);
            } catch (IllegalArgumentException e) {
                LOGGER.error("[TimelinePanel] 鏃犳晥鐨勫鍣↖D鏍煎紡: {}", containerId, e);
                return;
            }
            
            // 2. 鑾峰彇瀹瑰櫒
            Container container = Objects.requireNonNull(ContainerManager.getInstance()).getContainer(containerUuid);
            if (container == null) {
                LOGGER.warn("[TimelinePanel] 瀹瑰櫒涓嶅瓨鍦? {}", containerId);
                return;
            }
            
            // 3. 鍒涘缓瀹瑰櫒杞ㄩ亾锛堜娇鐢║I灞傜殑TimelineTrack锛?
            TimelineTrack track = new TimelineTrack(container.getName());
            track.setName("Track_" + container.getName());
            
            // 4. 娣诲姞鍒版椂闂寸嚎妯″瀷
            model.addTrack(track);
            
            // 5. 鏍囪鏁版嵁宸叉敼鍙?
            markDataChanged();
            
            LOGGER.info("[TimelinePanel] 鎴愬姛鍒涘缓瀹瑰櫒杞ㄩ亾: {} -> {}", 
                container.getName(), track.getName());
                
        } catch (Exception e) {
            LOGGER.error("[TimelinePanel] 澶勭悊瀹瑰櫒鎷栨斁澶辫触: containerId={}", containerId, e);
            // 鍙互鑰冭檻鍦ㄨ繖閲屾坊鍔犵敤鎴烽€氱煡锛堝 ImGui 寮圭獥锛?
        }
    }

    @Override
    public void onContainerDroppedToTrack(String containerId, int trackIndex) {
        // 娉ㄦ剰锛氭鍥炶皟鐜板湪鐢?TimelineDragAndDropHandler 鍐呴儴澶勭悊
        // 淇濈暀姝ゆ柟娉曚互鍏煎鎺ュ彛锛屼絾瀹為檯閫昏緫宸插湪 handleDragDropTarget 涓疄鐜?
        LOGGER.debug("[TimelinePanel] onContainerDroppedToTrack 鍥炶皟琚皟鐢紝浣嗛€昏緫宸茬敱 DragDropHandler 澶勭悊");
    }


    @Override
    public void onContainerDropRejected(String containerId, int trackIndex) {
        if (containerId == null || containerId.isEmpty()) {
            LOGGER.warn("[TimelinePanel] 瀹瑰櫒ID涓虹┖锛屾嫆缁濇嫋鏀?");
            return;
        }
        
        try {
            // 1. 楠岃瘉骞惰В鏋愬鍣↖D
            UUID containerUuid;
            try {
                containerUuid = UUID.fromString(containerId);
            } catch (IllegalArgumentException e) {
                LOGGER.error("[TimelinePanel] 鏃犳晥鐨勫鍣↖D鏍煎紡: {}", containerId, e);
                return;
            }
            
            // 2. 鑾峰彇瀹瑰櫒
            Container container = Objects.requireNonNull(ContainerManager.getInstance()).getContainer(containerUuid);
            if (container == null) {
                LOGGER.warn("[TimelinePanel] 瀹瑰櫒涓嶅瓨鍦? {}", containerId);
                return;
            }
            
            // 3. 鑾峰彇鐩爣杞ㄩ亾
            if (trackIndex < 0 || trackIndex >= model.getTrackCount()) {
                LOGGER.warn("[TimelinePanel] 鐩爣杞ㄩ亾绱㈠紩鏃犳晥: {} (鎬昏建閬撴暟: {})", 
                    trackIndex, model.getTrackCount());
                return;
            }
            
            TimelineTrack targetTrack = model.getTrackAt(trackIndex);
            if (targetTrack == null) {
                LOGGER.warn("[TimelinePanel] 鐩爣杞ㄩ亾涓嶅瓨鍦紝绱㈠紩: {}", trackIndex);
                return;
            }
            
            // 4. 璁板綍鎷掔粷鍘熷洜
            String containerName = container.getName();
            String trackTypeName = targetTrack.getType().getDisplayName();
            
            LOGGER.warn("[TimelinePanel] 瀹瑰櫒鎷栨斁琚嫆缁? {} 涓嶈兘鎷栨斁鍒?{} 杞ㄩ亾涓?(杞ㄩ亾绱㈠紩: {})", 
                containerName, trackTypeName, trackIndex);
            
            // 5. 鍙互鍦ㄨ繖閲屾坊鍔犵敤鎴风晫闈㈡彁绀猴紝姣斿鏄剧ず涓€涓复鏃剁殑閿欒娑堟伅
            // 鐢变簬ImGui鐨勯檺鍒讹紝杩欓噷鍙褰曟棩蹇楋紝瀹為檯搴旂敤涓彲浠ラ€氳繃鍏朵粬鏂瑰紡鏄剧ず鎻愮ず
            
        } catch (Exception e) {
            LOGGER.error("[TimelinePanel] 澶勭悊瀹瑰櫒鎷栨斁鎷掔粷澶辫触: containerId={}, trackIndex={}", 
                containerId, trackIndex, e);
        }
    }

    // ========== 鍥炶皟鎺ュ彛瀹炵幇锛圱imelineControlBar.Callbacks锛?=========
    
    @Override
    public void onAddTrackClicked() {
        // 濮旀墭缁?TrackController
        trackController.openMenu();
    }

    @Override
    public void onDeleteSelectedClicked() {
        deleteSelectedClip();
    }

    @Override
    public void onFitToViewClicked() {
        // 濮旀墭缁?NavigationHandler
        float availableWidth = ImGui.getContentRegionAvailX() - TimelineRenderer.getLeftLabelWidth();
        navigationHandler.fitToView(availableWidth);
    }
    
    // ========== 宸ュ叿鏂规硶 ==========
    
    private float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    @Override
    public int getPriority() {
        return 200;
    }

    // ========== 瀹瑰櫒鐩戝惉鍣ㄦ帴鍙ｅ疄鐜?==========

    @Override
    public void onContainerAdded(Container container) {
        // 瀹瑰櫒娣诲姞鏃朵笉闇€瑕佺壒娈婂鐞?
    }

    @Override
    public void onContainerRemoved(Container container) {
        // 瀹瑰櫒鍒犻櫎鏃跺凡缁忓湪DeleteContainersCommand涓垹闄や簡瀵瑰簲鐨勭墖娈?
    }

    @Override
    public void onContainerMoved(Container container, Container newParent) {
        // 瀹瑰櫒绉诲姩鏃朵笉闇€瑕佺壒娈婂鐞?
    }

    @Override
    public void onSelectionChanged(java.util.Set<Container> selected) {
        // 瀹瑰櫒閫夋嫨鍙樺寲鏃朵笉闇€瑕佺壒娈婂鐞?
    }

    @Override
    public void onContainerRenamed(Container container, String oldName, String newName) {
        // 鈽?鍔熻兘锛氬鍣ㄩ噸鍛藉悕鏃讹紝鍚屾鍒版椂闂寸嚎涓婄殑鐗囨
        if (container == null || newName == null) {
            return;
        }
        
        var timelineService = com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance();
        timelineService.updateClipNamesForContainer(container.getId(), newName);
        markDataChanged();
        
        LOGGER.info("[TimelinePanel] 瀹瑰櫒 {} 閲嶅懡鍚嶅凡鍚屾鍒版椂闂寸嚎鐗囨: {} -> {}", 
            container.getName(), oldName, newName);
    }

    @Override
    public void onActiveContainerChanged(Container oldActive, Container newActive) {
        // 婵€娲诲鍣ㄥ彉鍖栨椂涓嶉渶瑕佺壒娈婂鐞?
    }
}

