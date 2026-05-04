package com.director.core.selection;

import com.director.client.renderer.SelectionRenderer;
import com.director.client.renderer.ToolPreviewManager;
import com.director.core.selection.tools.FaceHandleManager;
import com.director.core.selection.tools.boxselecttool.BoxSelectTool;
import com.director.core.selection.events.*;
import com.director.core.selection.block_filters.BlockFilter;
import com.director.core.selection.block_filters.BlockFilterPipeline;
import com.director.core.selection.block_filters.BlockFilterPresetManager;
import com.director.core.selection.block_filters.BlockFilters;
import com.director.core.selection.block_filters.BlockFilterRegistry;
import com.director.core.selection.block_filters.FilterIds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.director.client.gizmo.Axis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;

/**
 * 婵犵妲呴崑鎾跺緤娴犲鐤い鎰╁€楁稉?SelectionManager - 闂傚倸鍊搁崐椋庢閿熺姴纾婚柛鏇ㄥ瀬閸ヮ剙绠ユい鏃傛嚀娴滅偓鎱ㄥΟ绋垮姎濠碘€冲悑閵囧嫰寮捄銊ь啋婵犵绱曢崗妯讳繆閻戣棄唯闁靛／鍕ㄦ灆缂傚倸鍊搁崐鐑芥嚄閼搁潧鍨旈悗闈涙啞椤洟鏌￠崶銉ョ仼缂佺姵鐗曢埞鎴︽偐閸欏鎮欓梻?
 * <p>
 * 闂傚倷娴囧畷鍨叏閻㈢绀夋俊銈呮噹缁愭鏌￠崶銉ョ仾闁稿孩顨嗘穱濠囧Χ閸涱厽娈舵繛鎴炴尭缁夊綊寮婚妸銉㈡婵☆垯璀︽禒楣冩⒑缁嬪尅鍔熼柛瀣枛楠炲骞栨担鐟颁罕闂佸壊鍋嗛崰搴ｅ枈瀹ュ棛绡€闁冲皝鍋撻柛娑卞幐閺嬪懘姊洪崫鍕闁瑰啿绻愰銉╁礋椤掆偓缁剁偤骞栫划鍏夊亾瀹曞洨鏆犻梻浣筋嚙濮橈箓锝炴径鎰畺闁割偅娲栫壕鍧楁⒑椤掆偓缁夋挳鎯屽Δ鍛厱鐟滃酣銆冮崨鎵婵犵數鍋為幐濠氬春閸愵喖纾婚柟鍓х帛閻撳啰鎲稿鍫濈婵炲棙鎷查崶顒夋晬婵犲ň鍋撻柣鎺戯攻閵囧嫰寮介顫勃闂佺顑嗛崝娆撳蓟閵堝浼犻柕澶樺枟濮ｅ矂姊虹粙娆惧剱闁圭顭烽獮蹇涘川椤曞懏效闁硅壈鎻徊楣冨窗濞戞ǚ鏀介柣鎰綑閻忥箓鏌熺粙娆剧吋闁诡啫鍐剧叆闁割偅绻勯弻褍顪冮妶鍡楀Ё缂佹彃娼￠妴鍛村蓟閵夛妇鍘梺鍓插亝缁诲倿顢旈锔解拻濠㈣泛锕﹀瓭闂傚洤顦扮换婵囩節閸屾碍鐏撻梺绋块鐎涒晝鎹㈠☉銏犲耿闁归偊鍓涙导鍫熺節?
 * - 濠电姷鏁告慨浼村垂閻撳簶鏋栨繛鎴炲焹閸嬫挸顫濋悡搴㈢彎濡ょ姷鍋涢崯顖滄崲濠靛纾奸柕鍫濇噺濞呭﹪姊绘笟鈧褔藝椤撱垹纾块柛妤冨剱閸ゆ鏌￠崘銊у闁绘挻娲熼弻鐔兼倻濡櫣浠告繝銏ｆ硾鐎氼噣鍩€椤掑喚娼愭繛鍙夛耿閹繝鏁撻悩鍙夌€悗骞垮劚椤︻垶鏌嬮崶顒佺厽闁哄啠鍋撻柟顔煎€搁‖濠囨倻閼恒儮鎷?Toolbar闂?
 * - 闂傚倷绀侀幖顐λ囬柆宥呯？闁圭増婢樼粈鍫熺箾閸℃ê绔惧ù婊冪秺閺屾稓浠﹂幆褍骞忓┑掳鍊曢幊蹇涘磻閹邦喒鍋撶憴鍕婵炲眰鍊濋幃妯绘綇閵娧咁啎闁诲孩绋掗…鍥儗婵犲啨浜滈柟瀛樼箓椤忊晠鏌嶇拠鏌ュ弰濠殿喒鍋撻梺闈涚墕濡鈻撳鍫熲拺闁告稑锕ゆ慨宀勬倶韫囨梻鎳呯紒顔芥⒐缁轰粙宕ㄦ繝鍕箞闂備浇顫夐崕宕囧椤撶偟鎽ユ繝鐢靛仦濞兼瑩宕ョ€ｎ喖绀夋繛鍡楃箳閺?
 * - 闂傚倸鍊风粈渚€骞夐敓鐘冲仭闁挎洖鍊搁崹鍌炴煟閵忋埄鐒鹃柛姘愁潐閵囧嫰骞橀崡鐐典痪闂佺粯甯熼褔鈥︾捄銊﹀磯闁绘碍娼欐导鎰版⒒閸屾碍鍣洪柨鏇樺灲瀵鈽夊Ο閿嬬€婚梺璇″灠椤ㄥ孩绂嶉悙鍝勭劦妞ゆ帒瀚☉褏鈧鍠栨晶搴ｅ垝鐎ｎ亶鍚嬮柛銉到娴滈箖鏌ㄥ┑鍡椻偓鐟扳枍閸℃ǜ浜滈柟閭﹀枟濞呮洜绱掓潏銊﹀鞍闁瑰嘲鎳樺畷鐑筋敇閻旈攱鐝ｉ梻鍌欑劍濡炲潡宕戦崨瀛樻櫇闁靛繈鍊楀畵渚€鏌嶈閸撴盯骞夐幖浣告婵炲棗澧介崝閿嬬箾閺夋垵鎮戞繛鍏肩懅閻?/ 闂傚倸鍊风粈渚€骞栭锕€鐤い鎰堕檮閸嬪鏌ｉ幘鍐差唫婵炴垶纰嶅畷澶愭煏婵炑冨缁?
 * - 缂傚倸鍊搁崐鐑芥嚄閼搁潧鍨旈悗闈涙啞椤洟鏌￠崶銉ョ仼缂佺姵鐗曢埞鎴︽偐閸欏鎮欑紓浣插亾闁稿瞼鍋為悡蹇撯攽閻愯尙浠㈤柛鏂诲€栫换婵嬪焵椤掍礁顕遍柡澶嬪灩妤犲洭姊虹€圭姵銆冮柣鎺為檮瀵板嫰宕熼娑氬幍闂傚鍓氳ぐ鍐焵椤掍緡娈橀柛鎺撳笚缁绘繂顫濋鐔割仧闂備胶绮敋鐎规洦鍓熻棢婵鍩栭埛鎴︽煙缁嬫寧鎹ｉ柍钘夘樀閺岋絽螖閳ь剛鎹㈠Ο渚殨?SelectionFilterPanel 闂傚倸鍊搁崐鐑芥倿閿曗偓椤灝螣閼测晝鐓嬮梺鍓插亝濞叉﹢宕戦鍫熺厱闁斥晛鍟伴埊鏇㈡煛?
 * 
 * @author Director Team
 * @version 2.0
 */
public class SelectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionManager.class);
    private static SelectionManager INSTANCE;

    private SelectionTool currentTool;
    private final SelectionRegistry registry = new SelectionRegistry();
    private final List<Consumer<SelectionState>> selectionChangeListeners = new ArrayList<>();
    
    /** 闂傚倸鍊搁崐椋庢閿熺姴纾婚柛鏇ㄥ瀬閸ヮ剦鏁嶆繝濞惧亾闁绘帒锕ラ妵鍕冀椤愵澀娌梺鎶芥敱閻楃娀寮诲☉銏犵労闁告劗鍋撻悾鐑芥⒑?*/
    private final SelectionSet selectionSet = new SelectionSet();

    /** 闂傚倸鍊烽懗鍫曗€﹂崼銏″床闁归偊鍨禍鐟邦熆閼搁潧濮堟い銉ワ躬閺屽秹宕崟顒€娅ゅ銈冨劚閻楁捇寮婚弴锛勭杸濠电偞鍎虫禍鍓р偓瑙勬礀濞层倗绱為崟顖涒拻濞达絽鎽滅粔娲煛閳ь剚娼忛埞鎯т壕缂佹绋戠€氼厼鈻嶉悩宕囩瘈濠电姴鍊绘晶鏇㈡煢閸愵亜鏋涢柡灞剧洴瀵挳濡搁妷銉ヮ劀闂備礁鎲￠崺鍐磻閹剧粯鈷掑ù锝堟閸氬綊鏌涚€ｎ偆鈽夐摶鐐翠繆閵堝倸浜鹃梺瀹狀嚙缁夊綊鐛幒鎳虫梹鎷呯憴鍕建闂傚倷绀佹竟濠囧磻閸℃稑绐楅幖娣妸閳ь剙鍊垮濠氬Ψ閿旀儳骞愰梻浣稿閸嬪懐鎹㈠鍛傜喖鍩€椤掑嫭鈷戦梺顐ゅ仜閼活垱鏅堕濮愪簻闁挎棁妫勯ˉ瀣煃瑜滈崜姘跺传鎼淬劌绀夌€光偓閸曞灚鏅╅棅顐㈡处缁嬫垵鏁梻渚€娼чˇ顓㈠磿鏉堚晜顐芥い鏇楀亾婵﹨娅ｇ划娆戞崉閵娧屽敹闁诲氦顫夊ú锕傚礈閻旇偐宓侀柛鎰靛枛缁犲鎮楀☉娅亪顢撻幘缁樼厽闁绘ê鍘栭懜顏堟煕閺傝法效鐎殿喖鐤囩粻娑樷槈濞嗘垵寮虫繝鐢靛仦閸ㄧ厧鈻嶉弴鐘亾?*/
    private final FaceHandleManager faceHandleManager = FaceHandleManager.SHARED;

    // ===== 闂傚倸鍊搁崐鎼佸磹閹间焦鍋嬮柛鏇ㄥ灠閸ㄥ倿鏌ц箛鎾冲辅闁稿鎸搁～婵嬵敆婢跺﹤澹庢俊銈囧Х閸嬫稓鎹㈠鈧獮鎰節閸愩劎绐為柣蹇曞仧閸嬫挾娆㈡导瀛樷拻濞达絽鎲￠幆鍫ユ煕閻曚礁浜伴柟顔哄劜缁轰粙骞栭悙鈺佷壕闁稿瞼鍋涢悡娑㈡煕閹般劍娅呴柡瀣€垮娲礈閹绘帊绨撮梺鎼炲妼濠€閬嶆偡瑜斿?(v4.9) =====
    private Box activeResizeBaseBounds = null;
    private Box activeResizePreviewBounds = null;
    private Integer activeResizeFaceIndex = null;
    private double activeResizeOffset = 0.0;

    // ===== BOUNDS Gizmo闂傚倸鍊烽悞锔锯偓绗涘懐鐭欓柟杈鹃檮閸嬪鐓崶褎鎹ｉ柡鍕€垮缁樻媴閸涘﹤鏆堥梺鍝勭焿缂嶄礁鐣峰┑鍡欐殕闁逞屽墴閸┾偓妞ゆ巻鍋撻柣蹇斿哺閺佸啴濮€閻樺灚娈鹃梺鍛婃尫鐠佹煡宕戦幘缁橆棃婵炴垶鐟ラ弳鍫㈢磼閸撗嗘濞存粠浜濠氬Ω閵夈垺顫嶅┑顔筋殔濡瑧鐟ч梻鍌欑劍閻綊宕愰弴鐘亾濮橆厽绶叉い鏇秮椤㈡岸鍩€椤掆偓閻ｉ攱绺界粙鍨祮闂侀潧顭梽鍕敇閸ф鈷戦柤濮愬€曢弸鎴︽煟閻旀潙鍔﹂柟顔ㄥ棛鐤€闁哄啠鍋撻柛娆忕箻閺岀喖姊荤€靛壊妲紓?=====
    private Box activeBoundsGizmoMovePreviewBounds = null;

    // 闂佽崵鍠愮划搴㈡櫠濡ゅ懎绠查柛銉墮閻掑灚銇勯幒鍡椾壕濡炪倧瀵岄崹铏閹间緡鏁嶆繛鎴炲焹閺嬫牕顪冮妶鍡樺暗闁哥姵鎸搁～蹇斻偅閸愨晝鍘搁梺鍓插亝缁诲秴螣閳ь剙鈹戦悙纰樻嫛濞存粎鍋熼崚鎺戭潩閼搁潧鈧粯淇婇姘倯闁伙箑顭峰娲传閸曨偀鍋撴繝姘疇闁圭偓鍓氶悞浠嬫煥閻斿搫孝缂侇偄绉归幃瑙勬媴閸涘﹨纭€缂備讲鍋撳璺衡姇瑜版帗鍋愰柤纰卞劮閿涘嫮纾肩紓浣姑崫娲煙椤旇棄鍔ら柍璇查叄楠炲洭顢樺┑鍫濈闂備礁鎼ˇ顐﹀疾濠婂牆鍨傜€规洖娲ㄩ崡姘舵煛婢跺鐏嶉柡瀣叄閺屽秹濡烽妷銉︽瘣闁荤姳娴囩亸娆戞閹捐纾兼繛鎴炶壘閳峰﹦绱撴笟鈧·鍌炲磻婵犲洤绠氶柛鎰靛枛缁€瀣亜閹扳晛鐏俊顖欑窔濮婃椽骞栭悙鎻掑濡炪倖娲﹂崢鑲╁弲?
    private Box emptySelectionBounds = null;
    
    /** 濠电姷鏁搁崑娑㈡偤閵娧冨灊鐎光偓閸曞灚鏅為梺鍛婃处閸嬧偓闁哄閰ｉ弻鏇＄疀婵炴儳浜剧€规洖娲ㄩ悰顕€姊绘担鍛婂暈婵炶绠撳畷銏＄鐎ｎ亞锛欓梺缁樺姉閸庛倝鎮?*/
    private SelectionToolEventProcessor eventProcessor;
    
    // ================================
    // 婵犵妲呴崑鎾跺緤娴犲鑸?闂傚倷娴囬褎顨ョ粙鍖¤€块梺顒€绉寸壕濠氭煟閹邦剚鈻曟繛鎾愁煼閺屾稓浠﹂幆褍鏋欑紓浣瑰敾缂嶄線寮诲☉銏犲嵆闁靛鍎查悵顔尖攽閻愭彃鎮戞俊顐㈠暣瀵鈽夊鍡樺兊濡炪倖甯婇悞锕傚箯閸楃儐娓婚柕鍫濇缁椦囨煕鐎ｃ劌鈧繂顕ｇ紒妯肩瘈闁稿被鍊曢幃鎴︽⒑閹肩偛鍔楅柡鍛矒閹?(v3.1)
    // ================================
    
    /** 闂傚倸鍊风粈渚€骞栭銈傚亾濮樺崬鍘寸€规洝顫夌€靛ジ寮堕幋鐘垫毎濠电偞鎸婚崺鍐磻閹剧粯鐓欓柧蹇ｅ亞濞插瓨銇勯姀鈩冾棃鐎规洜鍠栭、鏇烆吋閸♀晜鐤侀梺鍝勭焿缁绘繂鐣烽悢纰辨晝闁冲灈鏅滅€垫牠姊绘笟鈧褍煤閿旀儳鏋堢€广儱顦伴弲顒勬煙閻楀牊绶茬紒鈧€ｎ偁浜滈柡鍥╁仦閸ｈ櫣绱撳鍛板闁?*/
    private boolean autoPreviewEnabled = true;
    
    /** 闂傚倸鍊烽懗鍫曗€﹂崼銏″床闁割偁鍎辩粈澶屸偓鍏夊亾闁告洦鍓欓崜鍐差渻閵堝棗绗傞柣鎺為檮缁傚秹濡舵径瀣幈闂侀潧顧€缁茶姤淇婇挊澶嗘斀妞ゆ柨鎼悘顔剧磼鏉堛劌娴柟顕呬邯瀹曘劑顢涘鍛潓婵犵數鍋涢悺銊у垝閹惧墎涓嶉柡宥庡幖閺嬩胶鈧箍鍎遍ˇ顖炴煁閸ャ劎绡€闂傚牊绋掗ˉ鈩冦亜閺傛妯€婵?SelectionFilterPanel 闂傚倸鍊搁崐鐑芥倿閿曗偓椤灝螣閼测晝鐓嬮梺鍓插亝濞叉﹢宕戦鍫熺厱闁斥晛鍟伴埊鏇㈡煛閸滀礁澧い顓炴健閹虫粓鎮介悙鍏夊亾閹烘搩娈介柣鎰典簻閻忊晜銇勯锝庢當闁宠棄顦埢搴∥熼悡搴⌒┑锛勫亼閸婃牠骞愰幖浣测偓锕€鐣￠柇锕€娈ㄦ繛鏉戝悑濞兼瑧绮诲杈ㄥ枑闊洦娲橀崗婊堟煃瑜滈崜鐔奉潖濞差亜宸濆┑鐘插暊閹风粯绻濆▓鍨灍闁圭鍟块悾?exclude_air闂傚倸鍊烽悞锔锯偓绗涘懐鐭欓柟瀵稿仧闂勫嫰鏌￠崘銊モ偓鑽ょ不閺傛鐔嗛柤鎼佹涧婵洨绱掗埀顒勫礃椤忎礁浜炬鐐茬仢閸旀碍绻涚拠褏鐣电€殿喖鐤囩粻娑樷槈濞嗘垵甯鹃梻浣虹《閸撴繈銆冮崼銉﹀€堕梺顒€绉甸悡鍐偡濞嗗繐顏褑浜槐鎺楊敊绾拌京鍚嬮梺杞扮劍閹瑰洭骞冮埡鍛闁绘垵妫欓ˉ?闂傚倸鍊搁…顒勫磻閸曨個娲Ω閳轰胶鏌у銈嗗姧缁犳垵效閺屻儲鐓欓梺顓ㄧ畱閺嬨倝鏌ｉ鐔峰摵闁哄矉缍侀獮瀣倻閸℃瑥濮烘繝娈垮櫙缁插墽鍒掗幘璇茶摕闁哄洢鍨归獮銏′繆閵堝懎鏁憸搴ｆ崲濞戙垹閱囨い鎺戝€搁崺灞筋渻閵堝棙绌跨紓宥佸亾缂備胶濮电粙鎴︼綖濠靛绀傞柤娴嬫櫇鍟搁梻鍌氬€风粈渚€骞夐敍鍕殰婵°倕鎳忛崑锟犳煥濠靛棭妲搁柣?*/
    private final BlockFilterPipeline globalPipeline = createInitialPipeline();

    private static BlockFilterPipeline createInitialPipeline() {
        var p = BlockFilterPresetManager.getPreset(FilterIds.PRESET_DEFAULT);
        if (p != null && !p.isEmpty()) return p;
        BlockFilter f = BlockFilterRegistry.getFilter(FilterIds.EXCLUDE_AIR);
        return new BlockFilterPipeline().add(f != null ? f : BlockFilters.nonAir());
    }

    private SelectionManager() {
        registerDefaultTools();
        // 闂備浇顕уù鐑藉磻閿濆纾归柡宥庡幖绾惧潡鏌ゅù瀣珕鐎规洖寮剁换婵囩節閸屾粌顣虹紓浣插亾闁糕剝顭囩弧鈧梻鍌氱墛缁嬫帡鏁嶅鍡欑闁稿繒鈷堝▓婊堟煛鐏炲墽娲寸€殿喕绮欐俊姝岊槻闁愁亜澧庣槐鎾存媴闂堟稑惟闂佸憡鏌ㄧ粔鍓佸垝鐎ｎ喖绠抽柡鍐ｅ亾鐎规洖顦妴鎺戭潩閻撳海浠悶姘ｆ櫊濮婄粯鎷呴挊澶婃優闂佺顑嗙粙鎴ｇ亱濠德板€愰崑鎾绘煃鐠囪尙效妤犵偞甯掕灃濞达綀顫夐悵顐︽煟鎼粹€冲辅闁稿鎹囬弻娑㈠即閵娿倗鏁栫紓浣介哺瀹€绋款潖閾忓湱鐭欓悹鎭掑妿椤斿洨绱撴担鍓叉Ч闁瑰憡濞婇悰顕€宕卞Ο鑲╃槇闂佹悶鍎崝灞剧閵忕媭娓婚柕鍫濇婢ь剟鏌ｉ悢婵嗘噺鐎氬鏌嶉崫鍕櫤闁?
        this.eventProcessor = null;
    }

    public static SelectionManager get() {
        if (INSTANCE == null) {
            INSTANCE = new SelectionManager();
        }
        return INSTANCE;
    }
    
    /**
     * 闂備浇顕уù鐑藉磻閿濆纾归柡宥庡幖绾惧潡鏌ゅù瀣珕鐎规洖寮剁换婵囩節閸屾粌顣虹紓浣插亾闁糕剝顭囩弧鈧梻鍌氱墛缁嬫帡鏁嶅鍡欑闁稿繒鈷堝▓婊堟煛鐏炲墽娲寸€殿喕绮欐俊姝岊槻闁愁亜澧庣槐鎾存媴闂堟稑惟闂佸憡鏌ㄧ粔鍓佸垝鐎ｎ喖绠抽柡鍐ｅ亾鐎规洖顦妴鎺戭潩閻撳海浠悶姘ｆ櫊濮婄粯鎷呴挊澶婃優闂佺顑嗙粙鎴ｇ亱濠德板€愰崑鎾绘煃?
     */
    private SelectionToolEventProcessor getEventProcessorInternal() {
        if (eventProcessor == null) {
            eventProcessor = new SelectionToolEventProcessor();
        }
        return eventProcessor;
    }

    /**
     * 婵犵數濮烽弫鎼佸磻濞戔懞鍥敇閵忕姷顦悗鍏夊亾闁告洦鍋夐崺鐐烘⒑閸忛棿鑸柛搴℃惈閵嗘帡鎳滈崗鍝ョ畾濡炪倖鐗楁笟妤呭磿閵夆晜鐓曟慨姗嗗墯瀹告繈鏌嶈閸撴繈锝炴径鎰畺闁割偅娲栫壕鍧楁⒑椤掆偓缁夋挳鎯?
     */
    private void registerDefaultTools() {
        // 闂傚倸鍊烽懗鍓佹兜閸洖鐤鹃柣鎰ゴ閺嬪秹鏌ㄥ┑鍡╂Ф闁逞屽厸缁舵艾鐣烽妸鈺婃晣鐟滃秹寮查悩缁樷拺闂傚牊渚楀Σ鍫曟煕鎼粹€宠埞妞?
        registry.register(new com.director.core.selection.tools.ClickSelectTool());
        registry.register(new com.director.core.selection.tools.magicwandtool.MagicWandTool());
        
        // 濠电姴鐥夐弶搴撳亾閺囥垹纾圭憸鐗堝坊閳ь剨绠撳畷鍫曗€﹂幋鐑嗗晬闂備焦鎮堕崕婊呬沪閼恒儲鐝氶梻鍌欑窔濞佳囁囬銏犵？闁告鍎愰崵?
        registry.register(new com.director.core.selection.tools.faceselecttool.FaceSelectTool());
        // 闂傚倸鍊风粈渚€骞夐敓鐘插瀭閻熸瑥瀚崣蹇曗偓瑙勬礀濞层劎绮堟繝鍕ㄥ亾楠炲灝鍔氭い锔垮嵆閹偟鎷犲顔惧數闁荤姾妗ㄧ拃锕傚磿韫囨柣浜滈煫鍥ь儏閸斻倝鏌嶈閸撴繈锝炴径鎰畺闁割偅娲栫壕鍧楁⒑椤掆偓缁夋挳鎯屽Δ鍛厱妞ゎ厽鍨靛▍姗€鏌涢悢閿嬪殗闁哄瞼鍠撶槐鎺懳熼搹鍦嚃闂備胶顭堥…鍫ュ磹閺嶎厹鈧啴濡烽埡鍌氣偓鐑芥煠閸濄儲鏆╅柕鍡樺姍濮婅櫣鍖栭弴鐔哥彅闂佹椿鍘奸崐鎼佹偩瀹勯偊娼╅柤鎼佹涧閳ь剙顭烽幃宄扳枎濞嗘垵鍩屾繛瀛樼矋閸庢娊鈥︾捄銊﹀磯闁绘艾鐡ㄩ。鑲╃磽娴ｇ懓鏁剧紓宥勭窔瀵鈽夊鍛澑濠殿喗锕╅崣鈧柛姘€垮娲偂鎼达絼绮甸梺鎼炲妼濞尖€愁嚕椤愩埄鍚嬮柛銉檮閸曞啴姊洪崗鑲┿偞闁哄懏绻堥獮澶庛亹閹烘挴鎷洪梺鍛婄☉閿曘倖鎱ㄩ崶鈺冪＜濠㈣泛顑嗙紞鎴︽煟閿濆洤鍘撮柟顔哄灲瀹曟鎮℃惔婵囨線濠碉紕鍋戦崐鏍暜婵犲洦鍊块柨鏇炲€稿Ч鏌ユ煕椤愮姴鍔滈柣?
        // registry.register(new com.director.core.selection.tools.geometry.GeometrySelectTool());
        registry.register(new com.director.core.selection.tools.brushselecttool.BrushSelectTool());
        registry.register(new com.director.core.selection.tools.lassoselecttool.LassoSelectTool());
        
        // 濠电姷鏁告慨浼村垂婵傜鏄ラ柡宥庡亜閺嬪牏鈧箍鍎遍悧鍡涘汲閻愮儤鐓熼幖娣焺閸熷繘鏌涢悩宕囧⒈闁瑰箍鍨归～婊堝焵椤掑嫬鏄ラ柍褜鍓氶妵鍕箳閸℃ぞ澹曟俊鐐€ч梽鍕珶閸℃稑鐒垫い鎺嶇閹兼悂鏌涢弬璺ㄧ劯妞ゃ垺鐟ㄧ粻娑樷槈濡嚎鍔戦弻鏇熷緞閸繂濮庣紓?
        registry.register(new com.director.core.selection.tools.boxselecttool.BoxSelectTool());
        
        LOGGER.info("[SelectionManager] Registered {} selection tools", registry.size());
    }

    /**
     * 闂傚倷娴囧畷鍨叏瀹曞洨鐭嗗ù锝堫潐濞呯姴霉閻樺樊鍎愰柛瀣典邯閺屾盯鍩勯崘銊︽儧婵炲瓨绮岀紞濠囧蓟閻旂厧绠氱憸宥夊汲鏉堛劊浜滈柕鍫濇噺閸ｄ粙鏌嶈閸撴繈锝炴径鎰畺闁割偅娲栫壕鍧楁⒑椤掆偓缁夋挳鎯?
     */
    public void setCurrentTool(String toolId, World world) {
        SelectionTool tool = registry.getTool(toolId);
        if (tool == null) {
            LOGGER.warn("[SelectionManager] 闂傚倸鍊风粈渚€骞栭锕€鐤柣妤€鐗婇崣蹇涙煟閵忕姷浠涢柛蹇旂矒閺屾盯顢曢敐鍡欘槬缂備讲鍋撻柛鈩冪⊕閻撴洟鏌曟径娑氬埌濠碘€炽偢楠炴牠寮堕幋顖濆惈闂佸搫鏈惄顖炵嵁閹烘妫樻繛鍡欏亾濮ｅ绻濋悽闈浶涢柛瀣尰閵囧嫰骞橀崡鐐典痪闂佺瀛╅〃濠囧蓟閺囩喎绶為柛顐ｇ箓婵秶绱撴担鐟版暰缂傚秳绶氬? {}", toolId);
            return;
        }

        // String previousToolId = currentTool != null ? currentTool.getId() : null; // 闂傚倸鍊风粈渚€骞栭鈶芥稑螖閸涱厾锛欓梺鑽ゅ枛閸嬪﹤銆掓繝姘厱閻忕偛澧介妴鎺楁煃缂佹ɑ鈷掗柍褜鍓欑粻宥夊磿鏉堚斁鍋撳鐓庡⒋闁糕斂鍨肩粻娑樷槈濞嗘垵骞?
        
        // 闂傚倸鍊烽懗鍫曗€﹂崼銉晞闁糕剝鐟ラ崹婵嬪箹濞ｎ剙濡奸柡瀣╃窔閺屾洟宕煎┑鍫濐暥婵炲瓨绮岀紞濠囧蓟閻旂厧绠氱憸宥夊汲鏉堛劊浜滈柕鍫濇噺閸ｄ粙鏌嶈閸撴繈锝炴径鎰畺闁割偅娲栫壕鍧楁⒑椤掆偓缁夋挳鎯?
        if (currentTool != null) {
            currentTool.onDeactivate(world);
            // 闂傚倸鍊风粈渚€骞夐敓鐘冲仭闁挎洖鍊搁崹鍌炴煟閵忋垺鏆╅柛妤佹皑閳ь剛鎳撶€氫即宕戞繝鍥х柈闁告稑鐡ㄩ悡蹇擃熆鐠鸿櫣澧曢柛鏃€姘ㄩ埀顒傛暩婢ф螞閸愵喖钃熸繛鎴炲焹閸嬫捇鏁愭惔鈥茬敖闂佹椿鍘奸惌鍌炲蓟閳╁啯濯撮梻鍫熺▓閸嬫捇寮介銈囩効閻庡箍鍎卞ú鐘诲磻閹剧粯鏅查柛灞剧⊕閻濇洖顪?
            getEventProcessorInternal().processToolDeactivation(currentTool.getId(), world);
        }

        // 闂佽姘﹂～澶愬箖閸洖纾块梺顒€绉撮惌妤呮煕瑜庨〃鍛矆閸℃稒鐓曢柕澶涚到婵″ジ骞栭弶鎴含闁哄矉绻濆畷姗€顢橀悙闈涘缚缂傚倷鐒﹂弻銊┾€﹂悜钘夌畺闁割偅娲栭獮銏′繆閵堝倸浜鹃悷婊呭閻熴儵婀佸┑鐘诧工妤犲憡绂嶉悧鍫㈢闂傚倹娼欏畵鍡橆殽閻愯尙绠荤€殿喕绮欐俊鎼佸Ψ椤旀槒鍊㈤梻鍌欒兌缁垶鈥﹂崼銉ユ槬闁哄稁鍘奸悞鍨亜閹达絾纭剁紒娑樼箳缁辨帗娼忛妸褏鐤勯梺鍝勮嫰濡繂鐣疯ぐ鎺濇晩闁绘挸姘︾粊鎾⒑閼姐倕校濞存粈绮欏畷婵堜沪閹屽殼闂佸湱澧楀妯肩矆閸岀偞鈷戦柛顭戝櫘閸庢垿鏌￠崱妯兼噰闁?
        faceHandleManager.cancelDrag();
        
        // 婵犵數濮烽弫鍛婃叏閻戝鈧倹绂掔€ｎ亞锛涢梺瑙勫劤椤曨厾绮绘ィ鍐╃厾缁炬澘宕晶浼存偨椤栨稓娲撮柡宀嬬磿娴狅箓寮妶鍛矗婵＄偑鍊栭弻銊ф崲濡绻嗛柣鎴ｆ鍞梺闈涱槶閸庡啿螣?
        currentTool = tool;
        currentTool.onActivate(world);
        
        // 闂傚倸鍊风粈渚€骞夐敓鐘冲仭闁挎洖鍊搁崹鍌炴煟閵忋垺鏆╅柛妤佹皑閳ь剛鎳撶€氫即宕戞繝鍥х柈闁告稑鐡ㄩ悡蹇擃熆鐠鸿櫣澧曢柛鏃€姘ㄩ埀顒傛暩婢ф顫忛崷顓熷床婵犻潧鐗嗛弸鍫熶繆椤栨繃銆冨瑙勬礃缁绘繈鎮介棃娑楀摋闁诲繐娴氭禍顏堛€佸▎鎰瘈闁告洦鍠栧皬闂備線娼ч…顓熶繆閸ヮ兙鈧?
        getEventProcessorInternal().processToolActivation(toolId, world);

        LOGGER.info("[SelectionManager] 闂備浇宕甸崰鎰垝鎼淬垺娅犳俊銈呮噹缁犱即鏌涘☉姗堟敾婵炲懐濞€閺岋絽螣閻戞ǚ鏋欓梺鍝ュ枎閹虫﹢寮婚弴鐔风窞闁割偅绻傛慨搴ㄦ倵閻㈤潧顣兼俊顐㈠暣瀵鈽夐姀鐘殿唺闂佺懓顕崑娑⑺囬埡鍛€甸悷娆忓缁€鈧悗娈垮枛婢у海鍒? {}", tool.getDisplayName());
    }

    /**
     * 闂傚倸鍊风粈渚€骞夐敍鍕殰闁圭儤鍤氬ú顏呮櫇闁逞屽墴閹箖鎮滈挊澶庢憰闂侀潧顦崕鎶剿囬锝勭箚妞ゆ劧瀵岄弳鎺楁煠閸愯尙鍩ｉ柟顔惧厴楠炴﹢寮妷锔绘綌闂備礁婀遍搹搴ㄥ垂閻楀牏顩查柣鎰靛墰缁犻箖鏌熺€电浠﹂柟顖氬闇夋繝濠傚閻鎮楅棃娑栧仮妤犵偛娲鍓佹崉椤垶袧?
     */
    public void switchToNextTool(World world) {
        SelectionTool nextTool = registry.getNextTool(currentTool);
        if (nextTool != null) {
            setCurrentTool(nextTool.getId(), world);
        }
    }

    /**
     * 闂傚倸鍊风粈渚€骞夐敍鍕殰闁圭儤鍤氬ú顏呮櫇闁逞屽墴閹箖鎮滈挊澶庢憰闂侀潧顦崕鎶剿囬锝勭箚妞ゆ劧瀵岄弳鎺楁煠閸愯尙鍩ｉ柟顔惧厴楠炲洭顢栭懞銉綌闂備礁婀遍搹搴ㄥ垂閻楀牏顩查柣鎰靛墰缁犻箖鏌熺€电浠﹂柟顖氬闇夋繝濠傚閻鎮楅棃娑栧仮妤犵偛娲鍓佹崉椤垶袧?
     */
    public void switchToPreviousTool(World world) {
        SelectionTool prevTool = registry.getPreviousTool(currentTool);
        if (prevTool != null) {
            setCurrentTool(prevTool.getId(), world);
        }
    }

    /**
     * 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ椤旂厧顫梺缁樺笩椤曆団€︾捄銊﹀磯闁绘碍娼欐导鎰版⒒閸屾碍鍣洪柨鏇樺灩椤繑绻濆顒€鑰垮┑掳鍊撶欢鈥澄涢悙鐑樷拺?
     */
    public SelectionSet getSelectionSet() { 
        return selectionSet; 
    }

    public FaceHandleManager getFaceHandleManager() {
        return faceHandleManager;
    }

    /**
     * 婵犵數濮烽弫鎼佸磻閻愬搫绠伴柟闂寸缁犵姵淇婇婵勨偓鈧柡瀣Ч楠炴牕菐椤掆偓婵′粙鏌ｉ幒鏇ㄥ殭妞ゎ叀娉曢幑鍕偖閺夋垳绱ｉ梻?
     */
    public void clearSelection() { 
        selectionSet.clear();
        emptySelectionBounds = null;
        
        // 婵犵妲呴崑鎾跺緤娴犲鐤い鏍仦閸?v2.0: 濠电姷鏁搁崑鐘诲箵椤忓棛绀婇柍褜鍓氱换娑欏緞鐎ｎ偆顦紓浣戒含閸嬬偛顕ラ崟顖氱疀妞ゆ巻鍋撻柛鐐姂濮婃椽宕楅懖鈹垮仦闂佸搫鎳忕换鍐嚗閸曨垰绀嬫い鏍ㄧ☉閳ь剙鐏氱换娑㈠箣閻戝棔鐥銈呯箰閻楀棙顢婇梻浣侯攰閹活亞绮婚幋锕€鍑犲〒姘ｅ亾闁哄本鐩、鏇㈩敃閵忕媭娼剧紓鍌欒閸嬫挸顭跨捄鍝勵槵闁稿鎸搁埢鎾诲垂椤旂晫浜梻浣告贡閳峰牓宕戞繝鍌滄殾闁告繂瀚€閺冣偓閹峰懐鎮伴埄鍐簷闂備浇顕ч崙鐣岀礊閸℃稑纾婚柟鐑橆殔绾捐崵鎲告惔锝嗗床婵炴垯鍨圭粻濠氭煕閹邦垰鐨哄Δ鐘叉喘濮婅櫣绱掑Ο璇茬濡炪倖鍨甸ˇ顖烆敋閿濆鏁嬮柍褜鍓欓悾閿嬬附缁嬪灝宓嗛梺缁樺姈閹本绂掕濮婄粯鎷呴崨濠冨創闂佺锕ㄩ～澶愬礆婵犲啰顩烽悗锝呯仛閺咃綁姊洪崨濠勭細闁稿氦娅曠粙?
        invalidateRendererCache();
        
        // 闂傚倸鍊烽懗鍫曞储瑜旈妴鍐╂償閵忋埄娲稿┑鐘诧工鐎氼參宕ｈ箛娑欑厓閺夌偘鍗冲鐑芥煃瑜滈崜姘辨暜閳ュ磭鏆﹂柕濞р偓閸嬫挸鈽夊▎妯煎姺閻熸粎澧楅崹鑸电┍婵犲浂鏁冮柨婵嗘閸戯繝鏌ｆ惔銏犲毈闁搞劌鐖奸弫鎰版倷瀹割喖鎮戦梺鍛婄矆閻掞妇绮?
        if (autoPreviewEnabled) {
            try {
                ToolPreviewManager.getInstance().clearPreview();
            } catch (Exception e) {
                LOGGER.debug("[SelectionManager] 婵犵數濮烽弫鎼佸磻閻愬搫绠伴柟闂寸缁犵姵淇婇婵勨偓鈧柡瀣Ч楠炴牠骞栭鐔虹獧缂備焦鍞荤紞渚€寮诲☉銏犲嵆闁靛鍎查悵顔尖攽閻愭彃鎮戞慨濠咁潐缁岃鲸绻濋崑鑺ユ閸┾偓妞ゆ巻鍋撻摶鐐翠繆閵堝懏鍣瑰鍛存⒑閻熼偊鍤熷┑顔炬暩閳ь剚鑹鹃…鐑藉蓟閺囩喎绶炴俊顖濇娴狀垳绱撴担鎻掍壕闂佸憡娲﹂崹閬嶆偂閺囩喓绠鹃柛鈩冣棨椤忓牊鍊堕柕澶嗘櫆閻撴洟鐓崶銊︾€奸柟瀵稿Х閻瑥顭跨捄渚叕闁告艾顑夐弻娑樷槈閸楃偞鐏曢梺鍛婂嚬閸嬪﹪寮婚敐鍡樺劅闁挎稑瀚弳娑㈡⒑閹肩偛濡芥俊鐐舵閻ｇ兘宕崟銊︻潔濠殿喗锚瀹曨剙鐣烽妷銉富闁靛牆妫涙晶锕傛煕婵犲喚娈樼紒? {}", e.getMessage());
            }
        }
    }

    /**
     * 婵犵數濮烽弫鎼佸磿閹寸姷绀婇柍褜鍓氶妵鍕即閸℃顏柛娆忕箻閺岋綁骞囬浣瑰創缂備讲鍋撻柛鈩冾樅瑜版帗鍤冮柍鍝勫婢舵劖鐓曢悗锝庡亝鐏忔壆鈧灚婢樼€氼厾鎹㈠┑瀣＜闁绘劖鎯岄崬?
     */
    public void addToSelection(BlockPos pos) { 
        if (pos != null) {
            emptySelectionBounds = null;
            selectionSet.add(pos);
            // 婵犵妲呴崑鎾跺緤娴犲鐤い鏍仦閸?v2.0: 濠电姷鏁搁崑鐘诲箵椤忓棛绀婇柍褜鍓氱换娑欏緞鐎ｎ偆顦紓浣戒含閸嬬偛顕ラ崟顖氱疀妞ゆ巻鍋撻柛鐐姂濮婃椽宕楅懖鈹垮仦闂佸搫鎳忕换鍐嚗閸曨垰绀嬫い鏍ㄧ☉閳ь剙鐏氱换娑㈠箣閻戝棔鐥銈呯箰閻楀棙顢婇梻浣侯攰閹活亞绮婚幋锕€鍑?
            invalidateRendererCache();
        }
    }

    /**
     * 闂傚倸鍊风粈浣虹礊婵犲偆鐒界憸蹇曟閻愬绡€闁搞儜鍥紬闂備礁鎲＄换鍌涘垔鏉堛劍鍎熼柨婵嗘閻濅即姊洪崷顓犲笡閻㈩垪鏅涢…鍥焼瀹ュ棌鎷洪梺鍛婄☉閿曘倗绮崒鐐寸厱婵°倓绀侀埢鏇㈡煕閳规儳浜炬俊鐐€栧濠氬磻閹炬番浜滈柨鏃囶嚙閻忥絽霉?
     */
    public void addAllToSelection(Collection<BlockPos> positions) {
        emptySelectionBounds = null;
        selectionSet.addAll(positions);
        // 婵犵妲呴崑鎾跺緤娴犲鐤い鏍仦閸?v2.0: 濠电姷鏁搁崑鐘诲箵椤忓棛绀婇柍褜鍓氱换娑欏緞鐎ｎ偆顦紓浣戒含閸嬬偛顕ラ崟顖氱疀妞ゆ巻鍋撻柛鐐姂濮婃椽宕楅懖鈹垮仦闂佸搫鎳忕换鍐嚗閸曨垰绀嬫い鏍ㄧ☉閳ь剙鐏氱换娑㈠箣閻戝棔鐥銈呯箰閻楀棙顢婇梻浣侯攰閹活亞绮婚幋锕€鍑?
        invalidateRendererCache();
    }

    /**
     * 濠电姷鏁搁崑娑㈩敋椤撶喐鍙忛柟缁㈠枟閻撯偓闂佹寧绻傞ˇ顖炴倿閸偁浜滈柟鍝勬娴滄儳顪冮妶蹇撶槣闁搞劏娅曠粋宥夊箹娓氬洦鏅┑鐘诧工閹虫劙鎮＄€ｎ偆绡€闁汇垽娼ф牎缂佺偓婢樼粔褰掑箖?
     */
    public void removeFromSelection(BlockPos pos) { 
        if (pos != null) {
            emptySelectionBounds = null;
            selectionSet.remove(pos);
            // 婵犵妲呴崑鎾跺緤娴犲鐤い鏍仦閸?v2.0: 濠电姷鏁搁崑鐘诲箵椤忓棛绀婇柍褜鍓氱换娑欏緞鐎ｎ偆顦紓浣戒含閸嬬偛顕ラ崟顖氱疀妞ゆ巻鍋撻柛鐐姂濮婃椽宕楅懖鈹垮仦闂佸搫鎳忕换鍐嚗閸曨垰绀嬫い鏍ㄧ☉閳ь剙鐏氱换娑㈠箣閻戝棔鐥銈呯箰閻楀棙顢婇梻浣侯攰閹活亞绮婚幋锕€鍑?
            invalidateRendererCache();
        }
    }
    
    /**
     * 婵犵妲呴崑鎾跺緤娴犲鐤い鏍仦閸?v2.0: 濠电姷鏁搁崑鐘诲箵椤忓棗绶ら柛鎾椻偓閸嬫挾绮☉妯诲櫤闁哥喐妞介弻娑㈠焺閸愵亖妲堥梺钘夊暟閸犳牠寮婚悢鐓庣闁归偊鍘捐ぐ褔姊虹紒妯肩細闁稿﹥顨堝Σ鎰板箻鐠囪尙锛滃┑鐐村灦閻熴儳鍠婂澶嬧拺閻庣櫢闄勫妯讳繆閻ｅ备鍋撻崹顐ｇ凡閻庢凹鍘奸…鍥疀濞戞鈺呮煥閺冨倹娅曠憸鐗堢懇濮婄粯鎷呴崷顓熻弴闂佸憡鏌ㄩ惉濂稿焵椤掍礁鍤柛銊ュ暱閳诲酣濮€閵堝棗浜滈梺缁樻尭濞寸兘藝椤曗偓濮婃椽宕橀崣澶嬪創闂佺锕ょ紞濠囧箖閳ユ枼鏋庨柟閭﹀枤椤?
     */
    public void setSelectionBoundsOverride(Box bounds) {
        emptySelectionBounds = copyBox(bounds);
        invalidateRendererCache();
    }

    public void clearSelectionBoundsOverride() {
        emptySelectionBounds = null;
        invalidateRendererCache();
    }

    private void invalidateRendererCache() {
        try {
            SelectionRenderer.getInstance().invalidateCache();
        } catch (Exception e) {
            // 濠电姷鏁告慨鐑姐€傛禒瀣劦妞ゆ巻鍋撻柛鐔锋健閸┾偓妞ゆ巻鍋撶紓宥咃躬楠?SelectionRenderer 濠电姷鏁搁崑鐐哄垂閸洖绠伴柛婵勫劤閻挾鐥幆褜鍎嶅ù婊冪秺閺岋紕浠︾拠鎻掑闂佺顑冮崝宥夊Φ閸曨垼鏁囬柣鏃堫棑椤戝倻绱撴担鎻掍壕闂佸憡娲﹂崹閬嶆偂濞戙垺鐓曟い鎰剁悼缁犳鈧稒绻冪换婵嗏枔閸喗鐏撻梺鍝ュУ閻楁洟鎮鹃悜钘夌闁芥ê锛夐埡鍐╁枑婵犲﹤瀚閬嶆煕瀹€鈧崑鐐烘偂閵夛妇绠鹃柟瀵稿剳娓氭稒淇婇悪娆忔噽绾惧ジ寮堕崼娑樺缂佺姵姘ㄧ槐鎺楁偐瀹曞洦鍒涢梺璇″枛婢ц姤绂掗敃鍌涘€锋い鎺戭槸閹牓姊婚崒姘偓鐑芥倿閿旈敮鍋撶粭娑樻处閸嬶繝鏌曟繛鐐珦闁?
            LOGGER.debug("[SelectionManager] 濠电姷鏁搁崑鐘诲箵椤忓棛绀婇柍褜鍓氱换娑欏緞鐎ｎ偆顦紓浣戒含閸嬬偛顕ラ崟顖氱疀妞ゆ巻鍋撻柛鐐姂濮婃椽宕楅懖鈹垮仦闂佸搫鎳忕换鍐嚗閸曨垰绀嬫い鏍ㄧ☉閳ь剙鐏氱换娑㈠箣閻戝棔鐥銈呯箰閻楀棙顢婇梻浣侯攰閹活亞绮婚幋锕€鍑犲〒姘ｅ亾闁哄本鐩、鏇㈡晲閸モ晝绐楀┑鐐茬摠缁矂濡堕幖浣哥畺鐟滅増甯掔粻娑㈡煃鏉炴壆顦﹀┑顕€顥撶槐鎾存媴娴犲鎽甸梺鑽ゅ暱閺呮盯鎮鹃悜鑺ユ櫜濠㈣埖蓱閺咁剙鈹戦悩缁樻锭婵☆偅顨婇悰顔锯偓锝庡亞缁犻箖鏌熺€电浠﹂悘蹇ｅ幘缁辨帗寰勭€ｎ偆绋囬梺鎸庢磸閸ㄤ粙銆侀弮鍫濆耿闊洦鎸锋竟鏇炍旈悩闈涗粶闁诲繑绻堥幃姗€顢旈崼鐔哄幈闂佸啿鎼崐濠氬Υ閹烘梻纾兼い鏃囶潐濞呭﹥銇勯姀锛勬创濠碘€崇埣瀹曞爼鍩℃繝鍐? {}", e.getMessage());
        }
    }

    /**
     * 濠电姷鏁告慨浼村垂閻撳簶鏋栨繛鎴炲焹閸嬫挸顫濋悡搴㈢彎濡ょ姷鍋涢崯顖滄崲濠靛鐐婇柕濞垮劗閸嬫捇鎮滈懞銉у幗闂佺粯鏌ㄩ幉锛勬閸欏鍙忛柨婵嗘嫅瀹搞儵妫佹径瀣瘈濠电姴鍊搁弸銈夋煕鎼粹€愁劉缂?
     */
    public void handleClick(World world, BlockPos pos, boolean shift) {
        LOGGER.info("[SelectionManager] Handle click pos={} shift={} currentTool={}",
            pos, shift, currentTool != null ? currentTool.getId() : "null");

        if (currentTool != null) {
            LOGGER.info("[SelectionManager] Dispatching tool click event");
            getEventProcessorInternal().processToolClick(world, pos, shift, false);

            LOGGER.info("[SelectionManager] Executing tool click logic");
            currentTool.onClick(world, pos, shift);

            LOGGER.info("[SelectionManager] Notifying selection change");
            notifySelectionChanged();
        } else {
            LOGGER.warn("[SelectionManager] No active tool to handle click");
        }
    }

    /**
     * 濠电姷鏁告慨浼村垂閻撳簶鏋栨繛鎴炲焹閸嬫挸顫濋悡搴㈢彎濡ょ姷鍋涢崯顖滄崲濠靛鐐婄憸蹇涱敊閸℃稒鈷戦柛婵嗗琚梺鍛婃煥閻倿銆侀弮鍫熷殐闁宠鍎虫禍鐐箾閸繄浠㈤柡瀣崌閺屾稓鈧綆鍋呯亸顓㈡煃?
     * @param hitOrNull 闂傚倷娴囬褏鎹㈤幇顔藉床闁归偊鍠掗崑鎾愁潩閻撳孩鐝濋悗瑙勬礀閻忔岸骞嗛弮鍫澪╅柕澶堝劤閻涒晠姊绘担鐑樺殌闁宦板妿閺侇噣鎮㈤悡搴″亶濡炪倖甯掗崐鑽ゅ閽樺褰掓晲閸偅缍堥柣蹇撶箣閸楁娊寮婚敐鍫㈢杸闁瑰灝鍟埢鍫ユ⒑鏉炴壆顦︾紒澶庡煐缁傛帡鏁冮崒娑樷偓閿嬨亜閹哄棗浜剧紓浣瑰敾缁犳挸顫忓ú顏勫窛濠电姴瀚ф慨鍥⒑閹肩偛濡界紒璇茬墦楠炲﹤鈻庨幘宕囶吅闂佹寧娲嶉崑鎾剁磼閻欏懐绉柡灞糕偓鎰佸悑闁告劑鍔岄‖澶愭⒒閸屾艾顏╃紒澶婄秺瀵濡堕崱娆撴婵犮垼娉涢ˇ閬嶆儎鎼淬劍鈷掑ù锝呭建閹烘鐤鹃柣妯哄棘濞戙垹绀冩い鏃傗拡濞煎﹪姊洪悙钘夊姕闁告挻鑹鹃…鍥箻缂佹ê浠┑鐘诧工鐎氼剟顢撳鍐炬富閻庨潧澹婇悞鐐亜椤忓嫬鏆ｇ€规洘甯掗埥澶婎潩閿濆棛浜?null
     */
    public void handleDragStart(World world, BlockPos start, BlockHitResult hitOrNull) {
        if (currentTool != null) {
            // 闂傚倸鍊风粈渚€骞夐敓鐘冲仭闁挎洖鍊搁崹鍌炴煟閵忋垺鏆╅柛妤佹皑閳ь剛鎳撶€氫即宕戞繝鍥х柈闁告稑鐡ㄩ悡蹇擃熆鐠鸿櫣澧曢柛鏃€姘ㄩ埀顒傛暩婢ф螞閸愵喖钃熼柣鏂垮悑閻撱儵鎮楅敐搴″妞ゎ値鍥ㄧ厽閹兼番鍨兼竟姗€鏌ｈ箛鏃傜疄妞ゃ垺宀搁弫鎰緞婵犲嫬濮︽俊鐐€栫敮濠囨倿閿曞倸鐭楅煫鍥ㄧ⊕閸婄敻鏌ㄥ┑鍡椻偓鐟扳枍閸ヮ剚鐓熼柕濠忕畱閻忋儵妫?
            getEventProcessorInternal().processToolDragStart(world, start);
            
            // 闂傚倸鍊风粈浣革耿闁秵鍋￠柟鎯版楠炪垽鏌嶉崫鍕偓褰掑级閹间焦鐓熼幖娣€ゅ鎰版煙椤旇偐鍩ｇ€规洘娲熼弻鍡楊吋閸涱垳鍘梻浣侯潒閸曞灚鐣剁紓浣哄閸ㄥ爼寮婚垾鎰佸悑闁告劑鍔岄‖鍫濐渻閵堝懐绠洪柛瀣姍閸┾偓妞ゆ帊绶￠崯蹇涙煕閻樺啿娴€规洘鍨块獮姗€寮妷锔芥澑婵＄偑鍊栧濠氬煕閸儱纾块柡鍐ㄧ墛閻撳啰鎲稿鍫濈婵﹩鍎甸敐澶婄闁哄啠鍋撴い鎰Г閵囧嫰骞囬崼鏇燁€嶉梺?-闂傚倸鍊风粈渚€骞夐敓鐘冲仭闁靛鏅涚壕鍦喐閻楀牆绗掓慨瑙勭叀閺岋綁寮崹顔藉€梺鍝勬媼閸撶喖骞冨鈧幃娆戞崉鏉炵増鐫忛梺鍝勵槴閺呮粓寮婚妸鈺佺厴闁硅揪绠戠壕鍏肩箾閹达綁鍝洪柣锝呫偢濮婅櫣绮欓懗顖ｆ蕉闂佸憡鏌ㄥù鐑藉礆閹烘鍨傛い鎰╁灮缁愮偞绻濋悽闈浶㈤柛濠傤煼楠炲啯绺介崨濠勫幗闂佺粯鍔曢顓㈠煟閵堝鐓曢柡鍐ｅ亾闁搞劎鏁婚垾锕傚炊閵娿儺鍤ら柣搴㈢⊕閿氶柍褜鍓涢崗姗€寮婚敐鍫Щ闂佺濮ょ划搴ｅ垝?
            currentTool.onDragStart(world, start, hitOrNull);

            // 闂備浇顕х€涒晠顢欓弽顓炵獥闁圭儤顨呯壕濠氭煙閻愵剚鐏遍柡鈧懞銉ｄ簻闁哄啫鍊甸幏锟犳煕鎼达紕绠伴棁澶愭煕韫囨洖甯跺┑顔肩Ф閳ь剝顫夊ú姗€銆冩繝鍥х畺婵犲﹤鎳愭す鎶芥倵閿濆骸浜濇い鎾虫健濮婃椽鎳￠妶鍛€鹃梺鑽ゅ枂閸庣敻骞冭閹瑩宕归顐ｇ稐婵犵數鍋涘Λ妤€霉濮橆優褰掑冀椤撶喓鍘遍梺鏂ユ櫅閸熶即鍩㈤幘缁樼厱閻庯綆鍋呯亸鎵偓鍨緲鐎氼厾鎹㈠┑鍥ㄥ闁惧繐婀遍崢顒勬⒒閸屾瑨鍏屾い顓炵墢閹广垽骞掑Δ鈧拑鐔奉熆閼搁潧濮囬柛姘愁潐缁绘繃绻濋崒姘亾闂佸摜鍠庨幊妯侯嚕閸洖閱囨い鎰垫線缁ㄨ棄顪冮妶蹇曠窗闁告濞婇獮?setPreviewBox 闂傚倷娴囬褎顨ョ粙鍖¤€块梺顒€绉寸壕濠氭煟閹邦剚鈻曟繛鎾愁煼閺屾稓浠﹂幆褍鏋欑紓浣瑰敾缂嶄線寮诲☉銏犲嵆闁靛鍎查悵顔尖攽閻愭彃鎮戞俊顐㈠暣瀵濡搁妷銏☆潔濠碘槅鍨甸崑鎰板礉椤栨稓绠鹃柡澶嬪灩濮ｇ偤鏌￠崨顖毿㈤柣锝呭槻铻栭柍褜鍓熼、妯荤附缁嬪灝绐涘銈嗘尨閸撴繈骞夋导瀛樷拻闁稿本鐟ч崝宥囨喐閺夊灝鏆熺紒顔芥閵囨劙骞掔€Ｑ冧壕闁稿瞼鍋涢悡娑㈡煕閹般劍娅呴柡瀣€垮娲礈閹绘帊绨肩紓渚囧枛闁帮綁骞?O(n^3) 濠电姷顣藉Σ鍛村磻閸涱収鐔嗘俊顖氱毞閸嬫挸顫濋悡搴ｄ桓闂佹寧绻勯崑娑㈩敇婵傜鐐婇柕濞垮労閳ь剚鐩娲箰鎼达絺妲堥柣搴㈠嚬閸ｏ絽顕ｉ幎绛嬫晢闁告洦鍓涢崣?
            String toolId = currentTool.getId();
            if (!"box".equalsIgnoreCase(toolId) && !"box_v3".equalsIgnoreCase(toolId)) {
                com.director.client.renderer.ToolPreviewManager.getInstance().startDragPreview(start);
            }
        }
    }

    /**
     * 濠电姷鏁告慨浼村垂閻撳簶鏋栨繛鎴炲焹閸嬫挸顫濋悡搴㈢彎濡ょ姷鍋涢崯顖滄崲濠靛鐐婄憸蹇涱敊閸℃稒鈷戦柛婵嗗琚梺鍛婃煥閻倿銆侀弮鈧缓浠嬪川婵犲嫬骞愰梻浣规偠閸庢粓宕ㄩ钘夋灈闂?
     * @param hitOrNull 闂傚倷娴囬褏鎹㈤幇顔藉床闁归偊鍠掗崑鎾愁潩閻撳孩鐝濋悗瑙勬礀閻忔岸骞嗛弮鍫澪╅柕澶堝劤閻涒晠姊绘担鐑樺殌闁宦板妿閺侇噣鎮㈤悡搴″亶濡炪倖甯掗崐鑽ゅ閽樺褰掓晲閸偅缍堥柣蹇撶箣閸楁娊寮婚敐鍫㈢杸闁瑰灝鍟埢鍫ユ⒑鏉炴壆顦︾紒澶庡煐缁傛帡鏁冮崒娑樷偓閿嬨亜閹哄棗浜剧紓浣瑰敾缁犳挸顫忓ú顏勫窛濠电姴瀚ф慨鍥⒑閹肩偛濡界紒璇茬墦楠炲﹤鈻庨幘宕囶吅闂佹寧娲嶉崑鎾剁磼閻欏懐绉柡灞糕偓鎰佸悑闁告劑鍔岄‖澶愭⒒閸屾艾顏╃紒澶婄秺瀵濡堕崱娆撴婵犮垼娉涢ˇ閬嶆儎鎼淬劍鈷掑ù锝呭建閹烘鐤鹃柣妯哄棘濞戙垹绀冩い鏃傗拡濞煎﹪姊洪悙钘夊姕闁告挻鑹鹃…鍥箻缂佹ê浠┑鐘诧工鐎氼剟顢撳鍐炬富閻庨潧澹婇悞鐐亜椤忓嫬鏆ｇ€规洘甯掗埥澶婎潩閿濆棛浜?null
     */
    public void handleDragUpdate(World world, BlockPos current, BlockHitResult hitOrNull) {
        if (currentTool != null) {
            // 闂傚倸鍊风粈渚€骞夐敓鐘冲仭闁挎洖鍊搁崹鍌炴煟閵忋垺鏆╅柛妤佹皑閳ь剛鎳撶€氫即宕戞繝鍥х柈闁告稑鐡ㄩ悡蹇擃熆鐠鸿櫣澧曢柛鏃€姘ㄩ埀顒傛暩婢ф螞閸愵喖钃熼柣鏂垮悑閻撱儵鎮楅敐搴″妞ゎ値鍥ㄧ厽閹兼番鍨兼竟妯荤箾鐏忔牑鍋撻幇浣告闁诲海鏁哥涵鍫曞磻閹炬枼妲堟繛鍛版珪閸ㄥ潡骞冮敓鐘茬劦妞ゆ帊鑳剁粻楣冩倵濞戞瑯鐒介柣顓烇攻閵囧嫰骞嬪┑鍡╀患闂?
            getEventProcessorInternal().processToolDragUpdate(world, current, current);
            
            // 闂傚倸鍊风粈浣革耿闁秵鍋￠柟鎯版楠炪垽鏌嶉崫鍕偓褰掑级閹间焦鐓熼幖娣€ゅ鎰版煙椤旇偐鍩ｇ€规洘娲熼弻鍡楊吋閸涱垳鍘梻浣侯潒閸曞灚鐣剁紓浣哄閸ㄥ爼寮婚垾鎰佸悑闁告劑鍔岄‖鍫濐渻閵堝懐绠伴柕鍫熸倐瀵鏁撻悩鑼€為悷婊冪箻楠炴鎮╃紒妯煎幐闁诲繒鍋為弸濠氱叕椤掑嫭鐓涢悘鐐跺Г閸ゅ洨鈧鍠楁刊鐣岀不濞戙垹鍗抽柣鎰問娴兼牠姊?-闂傚倸鍊风粈渚€骞夐敓鐘冲仭闁靛鏅涚壕鍦喐閻楀牆绗掓慨瑙勭叀閺岋綁寮崹顔藉€梺鍝勬媼閸撶喖骞冨鈧幃娆戞崉鏉炵増鐫忛梺鍝勵槴閺呮粓寮婚妸鈺佺厴闁硅揪绠戠壕鍏肩箾閹达綁鍝洪柣锝呫偢濮婅櫣绮欓懗顖ｆ蕉闂佸憡鏌ㄥù鐑藉礆閹烘鍨傛い鎰╁灮缁愮偞绻濋悽闈浶㈤柛濠傤煼楠炲啯绺介崨濠勫幗闂佺粯鍔曢顓㈠煟閵堝鐓曢柡鍐ｅ亾闁搞劎鏁婚垾锕傚炊閵娿儺鍤ら柣搴㈢⊕閿氶柍褜鍓涢崗姗€寮婚敐鍫Щ闂佺濮ょ划搴ｅ垝?
            currentTool.onDragUpdate(world, current, hitOrNull);

            // 闂傚倸鍊风粈渚€骞栭鈷氭椽濡舵径瀣槐闂侀潧艌閺呮盯鎷戦悢灏佹斀闁绘ɑ褰冩禍鐐烘煕閻旈攱鍤囬柡灞炬礃瀵板嫰宕煎┑鍡楊潛闁诲海鏁告晶妤呭蓟閵娿劎浜遍梻浣告惈椤︻垶鎮樺┑瀣垫晜妞ゆ帊闄嶆禍婊勩亜閹扳晛鐏弫鍫ユ⒑鏉炴壆顦︾紒澶屾嚀閻ｉ攱绺介崨濠備簽婵炶揪缍€椤寮抽悩缁樷拻闁稿本鐟х拹浼存煕鐎ｎ亜顏挊婵囥亜閺嶃劎鈽夋い鏇憾閺岀喖鎮ч崼鐔哄嚒缂備讲鍋撻柛宀€鍋為悡鐔兼煏閸繈顎楅柣蹇曞У缁绘盯宕奸悢濂夋闂侀€炲苯澧叉い顐㈩樀閹椽寮撮悩鎻掔亰闂佸憡鎸嗛崨顖滃姸闂佽鍑界紞鍡涘礈濮樻墎鍋?setPreviewBox 闂傚倷娴囬褎顨ョ粙鍖¤€块梺顒€绉寸壕濠氭煟閹邦剚鈻曟繛鎾愁煼閺屾稓浠﹂幆褍鏋欑紓浣瑰敾缂嶄線寮诲☉銏犲嵆闁靛鍎查悵顔尖攽閻愭彃鎮戞俊顐㈠暣瀵?
            String toolId = currentTool.getId();
            if (!"box".equalsIgnoreCase(toolId) && !"box_v3".equalsIgnoreCase(toolId)) {
                com.director.client.renderer.ToolPreviewManager.getInstance().updateDragPreview(current);
            }
        }
    }

    /**
     * 濠电姷鏁告慨浼村垂閻撳簶鏋栨繛鎴炲焹閸嬫挸顫濋悡搴㈢彎濡ょ姷鍋涢崯顖滄崲濠靛鐐婄憸蹇涱敊閸℃稒鈷戦柛婵嗗琚梺鍛婃煥閻倿銆侀弮鍫熷殐闁冲搫鍟伴敍婵囩箾鏉堝墽绉い顐㈩槺娴滄悂鎮ч崼銏㈩啎?
     * @param hitOrNull 闂傚倷娴囬褏鎹㈤幇顔藉床闁归偊鍠掗崑鎾愁潩閻撳孩鐝濋悗瑙勬礀閻忔岸骞嗛弮鍫澪╅柕澶堝劤閻涒晠姊绘担鐑樺殌闁宦板妿閺侇噣鎮㈤悡搴″亶濡炪倖甯掗崐鑽ゅ閽樺褰掓晲閸偅缍堥柣蹇撶箣閸楁娊寮婚敐鍫㈢杸闁瑰灝鍟埢鍫ユ⒑鏉炴壆顦︾紒澶庡煐缁傛帡鏁冮崒娑樷偓閿嬨亜閹哄棗浜剧紓浣瑰敾缁犳挸顫忓ú顏勫窛濠电姴瀚ф慨鍥⒑閹肩偛濡界紒璇茬墦楠炲﹤鈻庨幘宕囶吅闂佹寧娲嶉崑鎾剁磼閻欏懐绉柡灞糕偓鎰佸悑闁告劑鍔岄‖澶愭⒒閸屾艾顏╃紒澶婄秺瀵濡堕崱娆撴婵犮垼娉涢ˇ閬嶆儎鎼淬劍鈷掑ù锝呭建閹烘鐤鹃柣妯哄棘濞戙垹绀冩い鏃傗拡濞煎﹪姊洪悙钘夊姕闁告挻鑹鹃…鍥箻缂佹ê浠┑鐘诧工鐎氼剟顢撳鍐炬富閻庨潧澹婇悞鐐亜椤忓嫬鏆ｇ€规洘甯掗埥澶婎潩閿濆棛浜?null
     */
    public void handleDragEnd(World world, BlockPos end, BlockHitResult hitOrNull) {
        if (currentTool != null) {
            // 闂傚倸鍊风粈渚€骞夐敓鐘冲仭闁挎洖鍊搁崹鍌炴煟閵忋垺鏆╅柛妤佹皑閳ь剛鎳撶€氫即宕戞繝鍥х柈闁告稑鐡ㄩ悡蹇擃熆鐠鸿櫣澧曢柛鏃€姘ㄩ埀顒傛暩婢ф螞閸愵喖钃熼柣鏂垮悑閻撱儵鎮楅敐搴″妞ゎ値鍥ㄧ厽閹兼惌婢€娴溿垺绻涙径瀣婵″弶鍔欓獮鎺懳旈埀顒傜不婵犳碍鍊垫繛鎴烆仾椤忓牊鍎嶉柕蹇ョ磿缁犻箖鎮楀☉娆樼劷闁活厼锕ラ妵鍕箣濠靛棭浠奸梺?
            getEventProcessorInternal().processToolDragEnd(world, end, end);
            
            // 闂傚倸鍊风粈浣革耿闁秵鍋￠柟鎯版楠炪垽鏌嶉崫鍕偓褰掑级閹间焦鐓熼幖娣€ゅ鎰版煙椤旇偐鍩ｇ€规洘娲熼弻鍡楊吋閸涱垳鍘梻浣侯潒閸曞灚鐣剁紓浣哄閸ㄥ爼寮婚垾鎰佸悑闁告劑鍔岄‖鍫濐渻閵堝懐绠洪柛瀣姉濡叉劙骞樼€涙ê顎撻柣鐔哥懃鐎氼剟妫勫鍛斀闁绘劗顣介幏锟犳煕鐎ｎ亷韬€殿喖顭峰畷銊╁级閹存繂鎸ら梻濠庡亜濞诧箑煤閺嶎偆鍗氬┑鍌氭啞閳?-闂傚倸鍊风粈渚€骞夐敓鐘冲仭闁靛鏅涚壕鍦喐閻楀牆绗掓慨瑙勭叀閺岋綁寮崹顔藉€梺鍝勬媼閸撶喖骞冨鈧幃娆戞崉鏉炵増鐫忛梺鍝勵槴閺呮粓寮婚妸鈺佺厴闁硅揪绠戠壕鍏肩箾閹达綁鍝洪柣锝呫偢濮婅櫣绮欓懗顖ｆ蕉闂佸憡鏌ㄥù鐑藉礆閹烘鍨傛い鎰╁灮缁愮偞绻濋悽闈浶㈤柛濠傤煼楠炲啯绺介崨濠勫幗闂佺粯鍔曢顓㈠煟閵堝鐓曢柡鍐ｅ亾闁搞劎鏁婚垾锕傚炊閵娿儺鍤ら柣搴㈢⊕閿氶柍褜鍓涢崗姗€寮婚敐鍫Щ闂佺濮ょ划搴ｅ垝?
            currentTool.onDragEnd(world, end, hitOrNull);

            // 缂傚倸鍊搁崐鎼佸磹閻戣姤鍊块柨鏇炲€哥粻浼存偣閸パ勨枙闁绘帟濮ら妵鍕箛閳轰胶鍔撮梺鍝ュ枎閹虫﹢寮婚弴鐔风窞闁割偅绻傛慨搴ㄦ倵閻㈤潧顣奸柡灞诲姀閻忓姊洪崫鍕垫Ц闁诲繑绻堥、鏃堫敆娴ｅ吀绨诲銈嗗姂閸ㄧ粯鏅ラ梻浣芥〃缁€浣虹矓閻熸壆鏆﹀┑鍌氭啞閸嬪棗霉閿濆牜娼愰柡鍡欏█濮婂宕掑▎鎴ｇ獥闂佺顑呯€氭媽妫熷銈嗙墬缁嬫垿顢氶柆宥嗙厵闁绘鐗婄欢鑼磼閳ь剟宕掗悙瀵稿幗闂侀潧鐗嗘鎼佹倶閻樺磭绠鹃柛顐ゅ枎椤ュ鏌嶈閸撴繈锝炴径鎰仱闁哄洨濮撮崹婵嬫煕閹炬鎳愰悞濂告煟鎼搭垳绉甸柛鎾存皑閳?clearPreview闂?
            String toolId = currentTool.getId();
            if (!"box".equalsIgnoreCase(toolId) && !"box_v3".equalsIgnoreCase(toolId)) {
                com.director.client.renderer.ToolPreviewManager.getInstance().endDragPreview();
            }
            
            // 婵犵妲呴崑鎾跺緤娴犲鐤い鏍仦閸?v2.0: 濠电姷鏁搁崑鐘诲箵椤忓棛绀婇柍褜鍓氱换娑欏緞鐎ｎ偆顦紓浣戒含閸嬬偛顕ラ崟顖氱疀妞ゆ巻鍋撻柛鐐姂濮婃椽宕楅懖鈹垮仦闂佸搫鎳忕换鍐嚗閸曨垰绀嬫い鏍ㄧ☉閳ь剙鐏氱换娑㈠箣閻戝棔鐥銈呯箰閻楀棙顢婇梻浣侯攰閹活亞绮婚幋锕€鍑犲〒姘ｅ亾闁哄本鐩、鏇㈩敃閵忕媭娼剧紓鍌欒閸嬫捇鏌涢弴銊ョ仭闁绘挻鐟╁娲垂椤曞懎鍓虫繝鈷€灞藉⒋闁诡喗顨呴～婵嬫惞椤愩們鈧﹦绱撴担浠嬪摵閻㈩垽绻濋悰顔嘉熺亸鏍т壕婵炴垶顏鍫熷剭闁靛繈鍊栭埛鎺戙€掑顒佹悙濞存粍绻堥弻銈堛亹閹烘梻鏆梺璇″枔閸庨潧鐣锋總绋垮嵆闁绘劗鏁搁弶鎼佹⒒婵犲骸浜滄繛璇х畵楠炴劖绻濆顒€鎯為梺鍛婃处閸ㄩ亶鎮￠悢鎼炰簻闁归偊鍠栭弸搴∶瑰鍫㈢暫婵﹦绮粭鐔煎炊瑜岀花璇差渻閵堝繐鐦滈柛銊ㄦ珪缁傚秹骞栨笟鍥ㄦ櫖闂佺粯鍔樼亸娆撳传濡ゅ懏鈷戦柤濮愬€曢弸鎴炵節閵忊槅鐒界紒杈╁仦缁楃喖鍩€椤掑嫬钃?
            invalidateRendererCache();
            
            // 闂傚倸鍊搁崐椋庢閿熺姴纾婚柛娑卞弾濞尖晠鏌曟繛鐐珕闁稿鍊块弻銊╂偄閸濆嫅锝夋煟閹烘洦鍤欐い顓℃硶閹瑰嫭绗熼姘婵＄偑鍊曠换鍫ュ磿閻㈢钃熸繛鎴炃氶弸搴ㄦ煙椤栨凹娈犲ù婊呭仱閸┾偓?
            notifySelectionChanged();
        }
    }

    /**
     * 婵犵數濮甸鏍闯椤栨粌绶ら柣锝呮湰瀹曟煡鏌熼悧鍫熺凡缂佺媭鍠楅幈銊ノ熼幐搴ｃ€愰梺鍛婎殕婵炲﹪骞冨Δ鈧埥澶娾枍閾忣偄鐏撮柟顕嗙節閸┾偓?
     */
    public void tick(World world) {
        if (currentTool != null) {
            currentTool.tick(world);
        }
    }

    /**
     * 婵犵數濮烽弫鎼佸磿閹寸姷绀婇柍褜鍓氶妵鍕即閸℃顏柛娆忕箻閺岋綁骞囬鐓庡闂佺粯甯熼褔鈥︾捄銊﹀磯濞撴凹鍨伴崜鏉款渻閵堝懐绠為柛搴ｆ暬瀵鈽夊Ο閿嬬€婚梺璇″灠椤ㄥ孩绂嶉悙鍝勭劦妞ゆ巻鍋撻柣蹇斿哺閹繝鏁撻悩鑼暰闂佸憡娲﹂崹鎵不閿濆棛绡€闂傚牊绋掗幖鎰版煕閹惧磭鍩ｆ慨?
     */
    public void addSelectionChangeListener(Consumer<SelectionState> listener) {
        selectionChangeListeners.add(listener);
    }

    /**
     * 缂傚倸鍊搁崐椋庣矆娓氣偓钘濇い鏍亹閳ь剨绠戦悾锟犲箥閾忣偆鈧椽鏌熼崗鑲╂殬闁告柨绉归幃鐐垫嫚瀹割喚鍞甸柣鐘烘〃鐠€锕傚磿韫囨柣浜滈煫鍥ь儏閸旓箓鏌＄仦鍓р槈闁宠閰ｉ獮鍡樼附婢跺鏁栭梺閫炲苯澧柣蹇斿哺閹繝鏁撻悩鑼暰闂佸憡娲﹂崹鎵不閿濆棛绡€闂傚牊绋掗幖鎰版煕閹惧磭鍩ｆ慨?
     * 
     * @deprecated 闂備浇宕甸崰鎰垝鎼淬垺娅犳俊銈呮噹缁犱即鏌涘☉姗堟敾婵炲懐濞€閺岋絽螣濞嗘儳娈柣搴㈣壘椤︿即濡甸崟顔剧杸闁规崘娅曞▓顓㈡⒑鐎圭媭鍤欑紒缁橈耿瀵鏁愭径濠庢綂闂佸啿鎼崯顐ゆ暜閵娧呯＝濞达絼绮欓崫铏圭磼鐠囨彃鏆熺紒顔藉哺瀹曪繝鎮欓埡鍌涙澑闂備礁澹婇崑鍡涘窗閹扮増鍋╂い鎺戝閳锋垶绻涢懠顒傚笡濠电偛娲弻娑㈠棘濞嗘儳鍓抽梺鍏兼そ娴滃爼銆佸Δ鍛妞ゆ劑鍨昏ぐ澶嬩繆閻愵亜鈧洜鎹㈤幇鏉跨疇闊洦绋掗埛鏃傗偓骞垮劚椤︿即鎮″▎鎴犳／闁绘鐓堕幋婵冩瀺闁告稑鐡ㄩ悡?
     */
    @Deprecated
    @SuppressWarnings("unused")
    public void removeSelectionChangeListener(Consumer<SelectionState> listener) {
        selectionChangeListeners.remove(listener);
    }

    /**
     * 闂傚倸鍊搁崐椋庢閿熺姴纾婚柛娑卞弾濞尖晠鏌曟繛鐐珕闁稿鍊块弻銊╂偄閸濆嫅锝夋煟閹烘洦鍤欐い顓℃硶閹瑰嫭绗熼姘婵＄偑鍊曠换鍫ュ磿閻㈢钃熼柨婵嗘啒閻旂厧鍨傛い鏃€鍎崇敮鍧楁⒒娓氣偓閳ь剛鍋涢懟顖炲储閸濄儳纾奸柍褜鍓氶幏鍛叏閹邦亝鐫忓┑鐘灱閸╂牠宕濋弴鐘电焼?
     */
    private void notifySelectionChanged() {
        if (currentTool != null) {
            SelectionState state = currentTool.getCurrentSelection();
            
            // 闂傚倸鍊风粈渚€骞夐敍鍕殰婵°倕鍟伴惌娆撴煙鐎电啸缁惧彞绮欓弻鐔煎礈瑜忕敮娑㈡煟閹烘洦鍤欐い顓℃硶閹瑰嫭绗熼姘婵＄偑鍊曠换鍫ュ磿閻㈢钃熸繛鎴炃氶弸搴ㄦ煙椤栨凹娈犲ù婊呭仱閸┾偓妞ゆ帒瀚☉褏鈧鍠栨晶搴ｅ垝鐎ｎ亶鍚嬮柛銉到娴滈箖鏌ㄥ┑鍡椻偓鐟扳枍閸℃ǜ浜?
            Set<BlockPos> addedBlocks = new HashSet<>(); // 缂傚倸鍊搁崐鐑芥嚄閼稿灚鍙忔い鎾卞灩绾惧鏌熼崜褏甯涢柣鎾存礋閺屸€愁吋閸愩劌顬嬫繝鈷€灞奸偗闁哄矉缍佹俊鍫曞川椤撗傜磾闂備浇顕栭崰鏍椤撱垹鏋佹い鏇楀亾妞ゃ垺鐟╁畷婊嗩檨婵¤尙鍏樺铏规嫚閺屻儺鈧绻濋埀顒勬焼瀹ュ懐锛涢梺闈涚墕閹峰鎮楅懜鐐逛簻闁哄秲鍔庨埊鏇㈡煕鎼达絽鏋涢柡宀嬬節瀹曟﹢濡搁敂淇卞亹闂備焦鎮堕崐鏇⑺囬悽绋跨畺鐟滅増甯楅幆鐐烘煟閻斿搫顣奸柣蹇撴濮婄粯鎷呯粙璺ㄩ獓缂備緡鍠楅幐鎼佹偩閻戣姤鏅查柛娑樺€婚崰鏍х暦椤愶箑绀嬮梻鍫熺⊕椤?
            Set<BlockPos> removedBlocks = new HashSet<>(); // 缂傚倸鍊搁崐鐑芥嚄閼稿灚鍙忔い鎾卞灩绾惧鏌熼崜褏甯涢柣鎾存礋閺屸€愁吋閸愩劌顬嬫繝鈷€灞奸偗闁哄矉缍佹俊鍫曞川椤撗傜磾闂備浇顕栭崰鏍椤撱垹鏋佹い鏇楀亾妞ゃ垺鐟╁畷婊嗩檨婵¤尙鍏樺铏规嫚閺屻儺鈧绻濋埀顒勬焼瀹ュ懐锛涢梺闈涚墕閹峰鎮楅懜鐐逛簻闁哄秲鍔庨埊鏇㈡煕鎼达絽鏋涢柡宀嬬節瀹曟﹢濡搁敂淇卞亹闂備焦鎮堕崐鏇⑺囬悽绋跨畺鐟滅増甯楅幆鐐烘煟閻斿搫顣奸柣蹇撴濮婄粯鎷呯粙璺ㄩ獓缂備緡鍠楅幐鎼佹偩閻戣姤鏅查柛娑樺€婚崰鏍х暦椤愶箑绀嬮梻鍫熺⊕椤?
            SelectionChangedEvent event = new SelectionChangedEvent(
                null, // 缂傚倸鍊搁崐鐑芥嚄閼稿灚鍙忔い鎾卞灩绾惧鏌熼崜褏甯涢柣鎾存礋閺屸€愁吋閸愩劌顬嬫繝鈷€灞奸偗闁哄矉缍佹俊鍫曞川椤撗傜磾闂備浇顕栭崰鏍椤撱垹鏋佹い鏇楀亾妞ゃ垺鐟╁畷婊嗩檨婵¤尙鍏樺缁樻媴閸涘﹥鍎撳┑鈽嗗亝椤ㄥ﹤鐣烽幇鐗堝仭闂侇叏闄勭€靛矂姊虹粙鎸庢拱缂佸甯楅崚濠囧箻椤旇棄浠梺璇″幗鐢帗淇婃禒瀣厸闁糕剝顨堢粈鍞榣l
                state, addedBlocks, removedBlocks);
            
            // 闂傚倸鍊风粈渚€骞夐敓鐘冲仭闁挎洖鍊搁崹鍌炴煟閵忋垺鏆╅柛妤佸哺閺岋綁骞嬮敐鍛呮捇鏌ｉ幒鏇ㄥ殭妞ゎ叀娉曢幑鍕瑹椤栨艾澹堟俊鐐€曠换鍫ュ磿閻㈢钃熸繛鎴炃氶弸搴ㄦ煙椤栨凹娈犲ù婊呭仱閸┾偓妞ゆ帒瀚☉褏鈧鍠栨晶搴ｅ垝鐎ｎ亶鍚嬮柛銉到娴滈箖鏌ㄥ┑鍡椻偓鐟扳枍閸℃ǜ浜?
            getEventProcessorInternal().processSelectionChanged(null, event); // 缂傚倸鍊搁崐鐑芥嚄閼稿灚鍙忔い鎾卞灩绾惧鏌熼崜褏甯涢柣鎾存礋閺屸€愁吋閸愩劌顬嬫繝鈷€灞奸偗闁哄矉缍佹俊鍫曞川椤撗傜磾闂備浇顕栭崰鏍椤撱垹鏋佹い鏇楀亾妞ゃ垺鐟╁畷婊嗩檨婵¤尙鍏樺缁樻媴閸涘﹥鍎撳┑鈽嗗亝椤ㄥ﹤鐣烽幇鐗堝仭闂侇叏闄勭€靛矂姊虹粙鎸庢拱缂佸甯楅崚濠囧箻椤旇棄浠梺璇″幗鐢帗淇婃禒瀣厸闁糕剝顨堢粈鍞榣l
            
            // 闂傚倸鍊搁崐椋庢閿熺姴纾婚柛娑卞弾濞尖晠鏌曟繛鐐珕闁稿鍊块弻宥夊煛娴ｅ憡娈銈嗘礋娴滃爼寮婚敐鍛傜喖宕楅崗鍏肩槑闂備胶纭堕弲婵囩箾閳ь剟鏌″畝瀣М闁糕晪绻濆畷鎺戭潩閸欐ɑ妯婇梻鍌欐祰椤曟牠宕规导姝ゅ洭妫冨☉姘?
            for (Consumer<SelectionState> listener : selectionChangeListeners) {
                try {
                    listener.accept(state);
                } catch (Exception e) {
                    LOGGER.warn("[SelectionManager] Selection change listener threw an exception", e);
                }
            }
        }
    }

    // ========== Getters ==========

    public SelectionTool getCurrentTool() {
        return currentTool;
    }

    public SelectionRegistry getRegistry() {
        return registry;
    }

    public SelectionState getCurrentSelection() {
        return SelectionState.of(selectionSet.asUnmodifiableSet());
    }
    

    public boolean hasCurrentTool() {
        return currentTool != null;
    }

    public String getCurrentToolId() {
        return currentTool != null ? currentTool.getId() : null;
    }

    public String getCurrentToolDisplayName() {
        return currentTool != null ? currentTool.getDisplayName() : "No active tool";
    }

    /**
     * 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ椤旂厧顫╃紓浣插亾闁稿瞼鍋為悡蹇撯攽閻愯尙浠㈤柛鏂诲€栫换婵嬪焵椤掍礁顕遍柡澶嬪灩妤犲洭姊虹€圭姵銆冮柣鎺為檮瀵板嫰宕熼娑氬幍闂傚鍓氳ぐ鍐焵椤掍緡娈橀柛鎺撳笚缁绘繂顫濋鐔割仧闂備胶绮敋鐎规洦鍓熻棢?
     * 
     * @return 闂傚倸鍊烽懗鍫曗€﹂崼銏″床闁割偁鍎辩粈澶屸偓鍏夊亾闁告洦鍓欓崜鍐差渻閵堝棗绗傞柣鎺為檮缁傚秹濡舵径瀣幈闂侀潧顧€缁茶姤淇婇挊澶嗘斀妞ゆ柨鎼悘顔剧磼鏉堛劌娴柟顕呬邯瀹曘劑顢涘鍛潓婵犵數鍋涢悺銊у垝鐏炵偓娅犲ù鐘差儏閻掑灚銇勯幒鍡椾壕濠电姭鍋撻梺顒€绉寸壕缁樹繆椤栨艾鎮戞い鎰矙閺屾洘寰勯崱妯荤彆闂佸搫鎷嬮崜鐔煎箖濮椻偓閹瑩鎳滃▓鎸庮棄闂佽崵濮抽悞锕傛偂閿熺姴钃熼柨婵嗩檧缂嶆牗銇勯幒鍡椾壕婵犲痉銈嗩仩缂佽鲸甯￠垾锕傚箣濠靛棌鎷柣搴ゎ潐濞叉粍绻涢埀顒傗偓瑙勬处閸嬪﹤鐣烽悢纰辨晣闁绘劘灏欓濂告⒒閸屾艾鈧娆㈤敓鐘茬；闁告洦鍨侀崶顒€绠ユい鏃傛嚀娴滅偓鎱ㄥΟ绋垮姎濠碘€冲悑閵囧嫰寮捄銊ь啋婵犵绱曢崗妯讳繆閻戣棄唯闁靛／鍕ㄦ灆
     */
    public BlockFilterPipeline getPipeline() {
        return globalPipeline;
    }

    /**
     * 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ椤旂厧顫梺绋款儍閸旀垿寮婚弴鐔虹闁割煈鍠掗崑鎾澄旈崨顔兼疄婵°倧绲介崯顖炴偂濞戙垺鐓曢悘鐐插⒔閻鏌涢妶鍌氫壕濠电姷鏁搁崑鐔煎Υ鐎ｎ剚宕叉繝闈涱儏閽冪喖鏌涘☉鍗炴灈缂佸墎鍋ら幃妤呮晲鎼存繃鍠氭繛瀵稿У閹稿啿顫忛崫鍕懷囧炊瑜嶉‖鍫ユ煟鎼淬垹鎼搁柛鏃€鍨垮畷娲焵椤掍降浜滈柟鍝勬娴滈箖姊洪崫鍕⒈闁告挻鐟╅弫鍐閿涘嫮鎳濋梺閫炲苯澧い鏇秮椤㈡洟鏁冮埀顒勬煁閸ヮ剚鐓ユ繝闈涙瀹告繈鏌ｉ敐鍛儓闁宠鍨块幃鈺侇啅椤旀儳濮奸梻浣侯焾閿曘儱煤閺嶎厼鐓濈€广儱顦粻铏繆閵堝倸浜鹃弶鈺傜箞閹鐛崹顔煎闂佸摜鍠撴繛鈧€规洘鍨块獮妯兼嫚閼碱剨绱叉繝鐢靛閸涱厺澹曢梺鍝勬噽閸嬬喓妲愰幘瀛樺闁告繂瀚烽埀顒佹尵缁辨帞绱掑Ο鑲╃暫缂?= 闂傚倸鍊烽懗鍫曗€﹂崼銏″床闁割偁鍎辩粈澶愭煙鏉堝墽鐣辩痪鎯ф健閺岀喓绱掗姀鐘崇亶闂佺粯甯炵划顖炲箞閵娿儙鐔兼惞閻у摜绀婄紓鍌欒兌婵敻鎮ч悩璇茶摕闁靛鍎Σ鍫熶繆椤栨繂浜瑰ù鐓庢喘濮?pipeline.test 闂傚倷娴囧畷鍨叏閺夋嚚褰掑磼閻愭彃鍋嶉梺鎯х箰濠€杈ㄧ▔瀹ュ鐓熼柡鍌涘閹叉悂鏌嶉悷鎵ｇ紒缁樼箞濡啫鈽夐崘鎻掝潬婵犵鍓濊ぐ鍐偋閹捐钃?
     */
    public BlockFilter getEffectiveBlockFilter() {
        return globalPipeline::test;
    }

    /**
     * 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ椤旂厧顫梺绋款儍閸旀垿寮婚弴鐔虹闁割煈鍠掗崑鎾澄旈崨顔兼疄婵°倧绲介崯顖炲煕閹烘鐓曢柕澶樺灣閸掓澘霉濠婂牏鐣洪柡?BlockFilterPipeline 闂傚倸鍊烽悞锕傛儑瑜版帒绀夌€光偓閳ь剟鍩€椤掍礁鎼搁柛鏃€鍨垮畷娲焵椤掍降浜滈柟鍝勬娴滈箖姊洪崫鍕⒈闁告挻鐟╅弫鍐閿涘嫮鎳濋梺閫炲苯澧い鏇秮椤㈡洟鏁冮埀顒勬煁閸ヮ剚鐓忛煫鍥э攻濞呭懘鏌熼鑽ょ獢婵﹦绮粭鐔煎炊瑜嶉～顏堟⒑閸︻収鐒鹃柣鏍с偢瀹曟椽鍩€椤掍降浜滈柟鍝勬娴滃墽绱撴担鍓叉Ъ婵炲鐩崺銏ゅ箻鐠囪尙顓洪梺鎸庢磵閸嬫挾绱掗埀顒勫醇閻旂繝绨婚梺鍝勭Р閸斿酣鍩婇弴銏＄厵闁绘垶鍨濋幉楣冩煛鐏炵偓绀嬬€规洖宕灃闁逞屽墴閸┾偓妞ゆ巻鍋撴い顓犲厴瀹曟椽鎮欓崫鍕敤濡炪倖鍔戦崐鎾剁箔婢舵劖鈷戦悹鎭掑妼閺嬫柨鈹戦鎸庣彧缂?= 闂傚倸鍊烽懗鍫曗€﹂崼銏″床闁割偁鍎辩粈澶愭煙鏉堝墽鐣辩痪鎯ф健閺岀喓绱掗姀鐘崇亶闂佺粯甯炵划顖炲箞閵娿儙鐔兼惞閻у摜绀婄紓鍌欒兌婵敻鎮ч悩璇茶摕?
     */
    public BlockFilterPipeline getEffectiveFilterPipeline() {
        return globalPipeline.clone();
    }

    // =====================================================
    // 婵犵妲呴崑鎾跺緤娴犲鐤い鏍仜閺?闂傚倸鍊搁崐鎼佸磹閹间焦鍋嬮柛鏇ㄥ灠閸ㄥ倿鏌ц箛鎾冲辅闁稿鎸搁～婵嬵敆婢跺﹤澹庢俊銈囧Х閸嬫稓鎹㈠鈧獮鎰節閸愩劎绐為柣蹇曞仧閸嬫挾娆㈡导瀛樷拻濞达絽鎲￠幆鍫ユ煕閻曚礁浜伴柟顔哄劜缁轰粙骞栭悙鈺佷壕闁稿瞼鍋涢悡娑㈡煕閹般劍娅呴柡?(v4.9)
    // =====================================================

    public void beginFaceResize(Box baseBounds, int faceIndex) {
        if (baseBounds == null) {
            cancelFaceResize();
            return;
        }

        activeResizeBaseBounds = copyBox(baseBounds);
        activeResizePreviewBounds = activeResizeBaseBounds;
        activeResizeFaceIndex = faceIndex;
        activeResizeOffset = 0.0;
    }

    public void previewFaceResize(int faceIndex, double offset) {
        if (activeResizeBaseBounds == null || activeResizeFaceIndex == null) {
            Box currentBounds = getCurrentBounds();
            if (currentBounds == null) {
                return;
            }
            beginFaceResize(currentBounds, faceIndex);
        }

        if (activeResizeFaceIndex == null) {
            return;
        }

        if (!activeResizeFaceIndex.equals(faceIndex)) {
            beginFaceResize(activeResizeBaseBounds != null ? activeResizeBaseBounds : getCurrentBounds(), faceIndex);
        }

        activeResizeOffset = offset;
        activeResizePreviewBounds = adjustBounds(activeResizeBaseBounds, activeResizeFaceIndex, activeResizeOffset);
    }

    public void applyFaceResize(int faceIndex, double offset) {
        if (activeResizeBaseBounds == null || activeResizeFaceIndex == null) {
            Box currentBounds = getCurrentBounds();
            if (currentBounds == null) {
                return;
            }
            beginFaceResize(currentBounds, faceIndex);
        }

        previewFaceResize(faceIndex, offset);

        if (activeResizePreviewBounds == null) {
            cancelFaceResize();
            return;
        }

        // 闂傚倷绀侀幖顐﹀疮閻楀牊鍙忓Δ锝呭枤閺佸﹪鏌熸潏鍓х暠闁告瑥锕ラ妵鍕冀閵娧屾殹闂佺楠搁敃顏堝蓟濞戞ǚ妲堟繛鍡楃箳娴滀即姊洪悡搴ｄ粵闁搞劌鐏濊灋闁告劦鍠楅崐椋庣棯閻楀煶鎴﹀疾閻愭祴鏀芥い鏃傚嵆閹存繍娼栫憸鐗堝笒閽冪喖鏌ｉ弮鍌氬付闂佽￥鍊栨穱濠囧Χ閸滀礁鍓崇紓浣插亾濠㈣埖鍔栭埛鎴炪亜閺冨洦顥夐柣蹇擄功缁辨帞绱掑Ο鍝勬懙闂佽鍠楅崕鎶藉煝鎼淬劌绠ｉ柣鎰摠椤撳綊姊绘担鐑樺殌妞ゆ洦鍙冨缁樺緞閹邦厼鍓梺缁樻⒒閸樠囧几娓氣偓閺屸剝寰勭€ｎ亞浠搁梺绋匡功閺佸寮婚敐澶娢╅柕澶堝労娴犲ジ鏌ｆ惔锛勪粵闁挎洏鍨归悾鐑藉礈娴ｆ彃浜鹃梻鍫熺⊕閸熺偟鐥紒顫偗闁诡喗顭堢粻娑㈠箻鐎涙ê鍓垫俊?
        emptySelectionBounds = copyBox(activeResizePreviewBounds);

        rebuildSelectionFromBounds(activeResizePreviewBounds);
        notifySelectionChanged();
        
        // 闂傚倸鍊风粈渚€骞栭鈷氭椽濡舵径瀣槐闂侀潧艌閺呮盯鎷?Gizmo 濠电姷鏁搁崑鐘诲箵椤忓棗绶ら柣锝呯灱閻捇鏌涢锝嗙闁稿浜弻娑㈠焺閸愬墽鍔峰銈傛櫇閸忔﹢寮婚弴鐔风窞闁割偅绻傞‖瀣渻閵堝懐宀搁柛瀣崌濮婂宕掑顑藉亾閹间礁绠犻幖杈剧稻瀹曟煡鏌熸潏鍓х暠闁藉啰鍠庨埞鎴︽偐閸欏顦╅梺绋块缁夊綊寮诲☉銏犲嵆闁靛鍎虫禒姘舵⒑缁嬫鍎愰柟姝岊嚙椤曘儵宕熼鍌滅槇闂佺鐬奸崑鐐侯敊閸愩劉鏀介柣鎰綑閻忥箓鏌熺亸鏍ㄦ珚鐎规洏鍨介幊鏍煛閸愵亜顦?
        updateGizmoPosition();
        
        cancelFaceResize();
    }

    public void cancelFaceResize() {
        activeResizeBaseBounds = null;
        activeResizePreviewBounds = null;
        activeResizeFaceIndex = null;
        activeResizeOffset = 0.0;
    }

    public boolean isFaceResizeActive() {
        return activeResizeBaseBounds != null;
    }

    public Box getResizePreviewBounds() {
        return activeResizePreviewBounds;
    }

    public boolean isBoundsGizmoMoveActive() {
        return activeBoundsGizmoMovePreviewBounds != null;
    }

    public Box getBoundsGizmoMovePreviewBounds() {
        return activeBoundsGizmoMovePreviewBounds;
    }

    public void setBoundsGizmoMovePreviewBounds(Box box) {
        activeBoundsGizmoMovePreviewBounds = box;
    }

    public void clearBoundsGizmoMovePreviewBounds() {
        activeBoundsGizmoMovePreviewBounds = null;
    }

    public Box getResizeBaseBounds() {
        return activeResizeBaseBounds;
    }

    private void rebuildSelectionFromBounds(Box bounds) {
        if (bounds == null) {
            return;
        }

        emptySelectionBounds = copyBox(bounds);

        selectionSet.clear();
        World world = MinecraftClient.getInstance() != null ? MinecraftClient.getInstance().world : null;
        if (world == null) {
            return;
        }

        int minX = (int) Math.floor(bounds.minX);
        int minY = (int) Math.floor(bounds.minY);
        int minZ = (int) Math.floor(bounds.minZ);
        int maxX = (int) Math.ceil(bounds.maxX) - 1;
        int maxY = (int) Math.ceil(bounds.maxY) - 1;
        int maxZ = (int) Math.ceil(bounds.maxZ) - 1;

        if (maxX < minX || maxY < minY || maxZ < minZ) {
            return;
        }

        // Bounds Gizmo resize闂傚倷鐒︾€笛呯矙閹烘鍤岄柟瑙勫姂娴滃湱鎲告惔锝囩煔闁告鍋愰弨浠嬫煕椤愩倕鏋戦柣锝庡墴濮婂宕掑鍗炩叡闂佺顑囬崰鏍春閻愬搫鍐€鐟滄粌顭囬埡鍛厸闁告劧绲芥禍楣冩⒑閸濆嫮鐏遍柛鐘崇墪閻?
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!world.isAir(pos)) {
                        selectionSet.add(pos);
                    }
                }
            }
        }
    }

    /**
     * 闂?闂傚倷鐒﹀鍨焽閸ф绀夐悗锝庡墲婵?BOUNDS Gizmo 婵犵數鍋犻幓顏嗙礊閳ь剚绻涙径瀣鐎殿噮鍋婃俊鑸靛緞鐎ｎ偅鐝繝娈垮枟閿曗晠宕㈤崗鐓庡姅婵犵數鍋為崹鍫曞箰閹绢喖纾婚柟鐐灱濡插牓鏌熼崹顔兼殨缂併劑顥撶槐鎾愁吋閸滀礁鍓崇紓渚囧枟濡啴銆佸☉姗嗘僵妞ゆ帒鍊归埢澶愭⒒娴ｈ鍋犻柛搴ㄤ憾椤㈡岸顢橀悘缁樻そ瀹曘劑寮堕幋鐘靛幀婵犵妲呴崹鎶姐€佹繝鍕焼閻庯綆鍠楅埛鎴炪亜閹扳晛鐒烘俊鍙夋倐閹绠涢敐鍛桓闂佽桨绀佺粔鐟扮暦閻撳簶鏀介柛鈩冪懄閳诲姊绘担鍛婂暈闁告垵缍婂畷鎴﹀川椤旇姤鐝烽悗骞垮劚椤︻垳鈧艾顦…璺ㄦ崉娓氼垱楔缂備礁顦顓犳閹捐绀冮柟顖嗗嫮褰茬紓鍌欐祰妞村摜鎹㈤崼婵堟殾闁绘顕у婵囥亜閺冨倵鎷″ù鐘插⒔缁辨挻鎷呴崫銉т紘闂佺绨洪崐婵嬪箚閸愵喗鍋ㄩ柛娑橈攻濞呮牗绻濋姀锝嗙【闁绘鍟村畷?
     *
     * <p>濠电姷鏁搁崑娑⑺囬銏犵鐎广儱顦粈鍫澝归悡搴ｆ憼闁哄拋鍓氶幈銊ノ熼搹鐧哥礊缂備胶濮寸紞濠傤潖濞差亜绠甸柟鐑樺焾濞堫厾绱撴担璇℃當妞わ箓浜堕獮蹇涱敃閿曗偓缁€鍐┿亜韫囨挻鍣烘繛?{@link #updateGizmoPosition()}闂傚倷鐒︾€笛呯矙閹达附鍤愭い鏍仜缁犳艾銆掑锝呬壕閻庤娲橀懝楣冨煝鎼淬劌绠涙い鎺戝€哥紞鍕⒒娴ｇ儤鍤€闁搞垺鐓￠崺鈧い鎺戝€搁弸鏃傜磼閻樺磭澧辩紒杈ㄥ笧閳ь剨缍嗘禍鍫曞磿鎼淬劍鐓犻柛顭戝亜閻忥箓鎮楅悽闈涘付妞ゎ偅绻堥、妤呭焵椤掑嫬绀冮柍褜鍓氱换婵嬪閿濆棛銆愮紓浣割槸缂嶅﹤鐣峰畷鍥╃煓婵炲棙鍎冲▓妯肩磽娴ｆ祴鍋撻搹顐簼zmo闂?
     * 闂備浇顕х花鑲╁緤缂佹鐝堕柛顐犲劚閸氬湱鎲稿鍫毎闂傚鍋勫ú锕€螞韫囨稑纾荤憸鏃堝蓟閿熺姴閱囨慨姗嗗厸婢规洖鈹戦悩顔肩伇闁糕晜鐗曢～婵嬪Ω閵堝洦妲€闂傚倷绀侀幉锟犲蓟閿濆绀夐悗锝庡墰閸楁岸鏌熺紒銏犳灈闁告劏鍋撶紓鍌欑劍椤ㄥ濡撮埀顒勬煕鐎ｎ偅灏伴柟宄版嚇閹兘骞嶉鑲╃С闂傚倷绀佺紞濠囧绩闁秴鍨傛繝闈涱儐閸ゅ牓鏌熸潏鍓х暠婵☆偅蓱閵囧嫰骞橀弶鎴闂?/p>
     */
    public void rebuildSelectionFromBoundsForBoundsGizmo(Box bounds) {
        if (bounds == null) {
            return;
        }

        emptySelectionBounds = copyBox(bounds);


        selectionSet.clear();
        World world = MinecraftClient.getInstance() != null ? MinecraftClient.getInstance().world : null;
        if (world == null) {
            SelectionEvents.fireChanged(SelectionChangeType.SELECTION_CLEARED);
            return;
        }

        int minX = (int) Math.floor(bounds.minX);
        int minY = (int) Math.floor(bounds.minY);
        int minZ = (int) Math.floor(bounds.minZ);
        int maxX = (int) Math.ceil(bounds.maxX) - 1;
        int maxY = (int) Math.ceil(bounds.maxY) - 1;
        int maxZ = (int) Math.ceil(bounds.maxZ) - 1;

        if (maxX < minX || maxY < minY || maxZ < minZ) {
            SelectionEvents.fireChanged(SelectionChangeType.SELECTION_CLEARED);
            return;
        }

        // Bounds Gizmo resize闂傚倷鐒︾€笛呯矙閹烘鍤岄柟瑙勫姂娴滃湱鎲告惔锝囩煔闁告鍋愰弨浠嬫煕椤愩倕鏋戦柣锝庡墴濮婂宕掑鍗炩叡闂佺顑囬崰鏍春閻愬搫鍐€鐟滄粌顭囬埡鍛厸闁告劧绲芥禍楣冩⒑閸濆嫮鐏遍柛鐘崇墪閻?
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!world.isAir(pos)) {
                        selectionSet.add(pos);
                    }
                }
            }
        }

        // 闂傚倸鍊风欢锟犲磻閸曨垁鍥敍濠娾偓閻掑﹥銇勯弽顐沪闁绘挸鍊婚埀顒€绠嶉崕閬嶅箠閹邦喗顫曢柨鏇炲€归悡鏇熸叏濮楀棗澧撮柛娆忓缁绘盯骞橀崘宸殺闂佺灏欐晶妤呭箯閸涱垳椹抽悗锝庝簼閺嗗繑绻濋悽闈涗沪闁瑰摜顭堥锝夊醇閺囩喎浜楅柣搴秵閸撴稓澹曟禒瀣厵闁诡垎鍜冪礊闂佸湱鏅慨鎾€﹂崸妤€绠氬ù鍏兼綑閹界敻姊洪崫鍕櫡闁稿海鏁诲顐㈩吋閸ワ附鍕冮梺鍏间航閸庨亶宕滈妸鈺傗拺闁告稑锕ょ粭姘舵煟閻旀潙鍔ラ柕鍥ㄥ姍瀵濡烽敃鈧▓鎴︽⒑閼恒儍顏埶囬姣挎帗寰勯幇顓犲幗?闂傚倸鍊风欢锟犲磻閸曨垁鍥敍濠娾偓閻掑﹥銇勯弽銊ョ劰闁哄矉绠撻弻宥夊煛娴ｅ憡娈茬紓浣鸿檸閸ㄥ爼寮婚敓鐘茬＜婵犻潧妫涢崝鐑芥⒑闁偛鑻崢鍛婁繆閻愭潙绗х紒顕呭幗瀵板嫬鐣濋埀顒勬儗濞嗘挻鐓曢柟閭﹀灠閻ㄦ椽鏌?闂傚倷鐒﹂惇褰掑礉瀹€鈧埀顒佸嚬閸ㄥ爼鏁愰悙纰樺亾閿濆骸澧柛鐘叉椤法鎹勯搹瑙勬闂侀€炲苯澧柛鐔告尦楠?
        invalidateRendererCache();

        if (selectionSet.isEmpty()) {
            SelectionEvents.fireChanged(SelectionChangeType.SELECTION_CLEARED);
        } else {
            SelectionEvents.fireChanged(SelectionChangeType.SELECTION_UPDATED);
        }

        // 闂備浇宕垫慨宕囨媼閺屻儱鐤鹃柣妯烘▕閻掕棄霉閸忓吋缍戠紒鐙€鍨堕弻鈥愁吋閸愩劌顬堝銈呯箰閵堟悂寮婚敐澶婄厸闁稿本姘ㄦ禒鎾⒑鐠団€崇仩妞わ箓娼ч悾鐑藉箮閽樺）鈺呮煏閸繃宸濇俊鎻掔箻濮婃椽鎮℃惔锝嗙€繝娈垮枔閸婃繂顕ｉ銉ｄ汗闁圭儤鍨归ˇ顐︽⒑閸濆嫷妲搁柣蹇旂箘缁辩偞绗熼埀顒勫蓟濞戙垹鍐€闁靛ě鍐炬缂傚倷娴囨ご鎼佹偡閿斿墽鐭欏璺侯煬閸氬鏌涢埡鍌氼嚋婵炶尙鍠庨悾鐑芥晲婢跺﹦鐤呮繝闈涱槺閻濐摟teGizmoPosition闂傚倷鐒︾€笛呯矙閹达附鍎楅柛灞剧☉椤曢亶鏌嶉崫鍕櫣缂佲偓閸屾壕鍋撻獮鍨姎婵☆偅顨婇幃闈涒槈閵忥紕鍘甸梻浣哥仢椤戝懘鎮樻繝鍥ㄧ厾闁割煈鍋勯悘瀵糕偓娈垮櫘閸撶喖銆佸鈧俊姝岊槾妞?
        notifySelectionChanged();
    }

    private Box adjustBounds(Box base, int faceIndex, double offset) {
        if (base == null) {
            return null;
        }

        double minX = base.minX;
        double minY = base.minY;
        double minZ = base.minZ;
        double maxX = base.maxX;
        double maxY = base.maxY;
        double maxZ = base.maxZ;

        double minThickness = 0.01;

        switch (faceIndex) {
            case 0 -> maxX = Math.max(minX + minThickness, maxX + offset);
            case 1 -> minX = Math.min(maxX - minThickness, minX - offset);
            case 2 -> maxY = Math.max(minY + minThickness, maxY + offset);
            case 3 -> minY = Math.min(maxY - minThickness, minY - offset);
            case 4 -> maxZ = Math.max(minZ + minThickness, maxZ + offset);
            case 5 -> minZ = Math.min(maxZ - minThickness, minZ - offset);
            default -> {
            }
        }

        if (maxX - minX < minThickness) {
            double mid = (minX + maxX) * 0.5;
            minX = mid - minThickness * 0.5;
            maxX = mid + minThickness * 0.5;
        }
        if (maxY - minY < minThickness) {
            double mid = (minY + maxY) * 0.5;
            minY = mid - minThickness * 0.5;
            maxY = mid + minThickness * 0.5;
        }
        if (maxZ - minZ < minThickness) {
            double mid = (minZ + maxZ) * 0.5;
            minZ = mid - minThickness * 0.5;
            maxZ = mid + minThickness * 0.5;
        }

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private Box copyBox(Box source) {
        return source == null ? null : new Box(source.minX, source.minY, source.minZ,
                source.maxX, source.maxY, source.maxZ);
    }
    
    /**
     * 闂傚倸鍊风粈渚€骞栭鈷氭椽濡舵径瀣槐闂侀潧艌閺呮盯鎷?Gizmo 濠电姷鏁搁崑鐘诲箵椤忓棗绶ら柣锝呯灱閻捇鏌涢锝嗙闁稿浜弻娑㈠焺閸愬墽鍔峰銈傛櫇閸忔﹢寮婚弴鐔风窞闁割偅绻傞‖瀣渻閵堝懐宀搁柛瀣崌濮婂宕掑顑藉亾閹间礁绠犻幖杈剧稻瀹曞弶鎱ㄥ璇蹭壕闂佺硶鏂侀崑鎾愁渻閵堝棗鍧婇柛瀣尰閵囧嫰鏁傜拠鑼化婵炲瓨绮嶉幃鍌炪€佸☉妯锋婵﹫绲鹃悘鍐╀繆閻愵亜鈧牠骞愭ィ鍐ㄧ；闁绘棃鏅茬换?
     */
    private void updateGizmoPosition() {
        // Gizmo 闂傚倸鍊烽悞锕€顪冮崹顕呯劷闁秆勵殔缁€澶愭倵閿濆骸澧插┑顔挎珪閵囧嫰骞掗幋婵愪患闂佽绻楀▍鏇犳崲濠靛洨绡€闁稿本鍑规禒鎯р攽閻愯尙澧ｆい鏇嗗洦绠掗梻浣瑰缁诲倸螞濞嗘垳鐒婇柛顭戝亞椤╁弶绻涢幋娆忕仾闁绘挻娲熼弻锟犲磼濠垫劕娈繝纰樷偓鐐藉仮闁诡喗顨婂畷褰掝敃椤掑啠鍋撶捄銊㈠亾鐟欏嫭绀€濠⒀冮叄閸┾偓妞ゆ帒瀚☉褔鏌熺拠褏纾垮?闂傚倸鍊搁崐椋庢閿熺姴纾婚柛鏇ㄥ瀬閸ヮ剦鏁嶆繝濞惧亾闁绘帒锕ラ妵鍕冀椤愵澀绮堕梺璇茬箺濞呮洜鎹㈠┑瀣瀭妞ゆ劧绲介弳妤冪磽娴ｆ彃浜炬繝銏ｅ煐閸旀牠鎮￠弴鐔虹闁糕剝鈼ら懖鈺勫С闁稿繗鍋愮弧鈧梺閫炲苯澧紒妤冨枛閸┾偓妞ゆ巻鍋撻柣锝囧厴閹粓鎸婃竟鈹炬櫊閺屻劑寮崶鍡樺浮瀵悂寮介銈囷紳婵炶揪绲捐ぐ鍐╃妤ｅ啯鈷戦柛娑橈功缁犳捇鎮楀鐓庡⒋闁诡垯绶氬畷濂稿即閻斿搫骞堥梻渚€娼чˇ顓㈠磿濞差亜鍚归柣鏂挎憸缁犻箖鏌熺紒妯虹瑲闁搞倕娲ㄩ埀顒侇問閸ｎ噣宕抽敐澶婄疇婵犲﹤鐗婇ˉ鍫熺箾閹达綁鍝洪柣搴亰濮婄粯鎷呴崨濠傛殘闂佸憡鍨电紞濠傜暦瑜版帒绠柦妯侯槹閻庮剟姊洪棃娑氬妞わ缚鍗冲鏌ヮ敆閸屾浜鹃柛蹇擃槸娴滈箖姊洪崨濠冨濞存粎鍋熷Σ鎰潩閼哥鎷虹紓浣割儓濞夋洜绮婚幍顔剧＜濠㈣泛锕﹂敍宥囩磼椤曞懎骞栭柣锝嗙箞瀹曠喖顢曢妶搴℃暩闂傚倷绀侀幖顐ょ矓閺屻儱绀夌€光偓閸曞灚鏅滈梺鍏肩ゴ閺備線宕戦幘缁樺€烽柣鐔稿閸嬫挸鈽夊Ο閿嬬€洪梺鎸庣箓閹灝煤椤忓嫬绐涘銈嗘⒒閾忓骸煤閹间焦鍋℃繝濠傚暟缁犺崵鈧鍣崑濠囩嵁濮椻偓楠炲洦鎷呴悷棰佹樊闂傚倷鑳堕…鍫ュ嫉椤掑嫭鍤屽Δ锝呭暙缁?濠电姷鏁搁崑娑㈡偤閵娾晛鍚归幖娣妼绾惧綊鏌熼悙顒€鍔跺┑顔煎暱闇夐柛蹇撳悑缂嶆垹绱掗埀顒勫礃椤旂晫鍘剧紒鐐緲瀹曨剚鏅堕幍顔瑰亾?
        if (!com.director.client.settings.RenderSettings.get().selection_showBoundingBox) {
            try {
                com.director.client.gizmo.GizmoManager.get().deactivate();
            } catch (Exception ignored) {
            }
            return;
        }

        Box bounds = getCurrentBounds();
        if (bounds == null) {
            return;
        }
        
        // 闂傚倷娴囧畷鍨叏瀹曞洦顐介柕鍫濇处椤洟鏌￠崶銉ョ仾闁稿鏅涢埞鎴︽偐鐎圭姴顥濆┑鈽嗗亝閿曘垽寮婚悢灏佹灁闁割煈鍠楅悘宥夋⒑娴兼瑧绉ù婊冪埣瀵鈽夐姀鐘殿吅闂佺粯蓱閻ｎ偊鏁嶉崟顓狅紳閻庡箍鍎遍幊鎰墡缂傚倷绀侀鍛此囬悽鎼炩偓浣割潨閳ь剟骞冨▎鎾搭棃婵炴垼椴搁崰?
        Vec3d newPivot = new Vec3d(
            (bounds.minX + bounds.maxX) / 2.0,
            (bounds.minY + bounds.maxY) / 2.0,
            (bounds.minZ + bounds.maxZ) / 2.0
        );
        
        try {
            com.director.client.gizmo.GizmoManager gizmoManager = com.director.client.gizmo.GizmoManager.get();
            if (gizmoManager != null && gizmoManager.isActive()) {
                // 闂?濠电姷鏁搁崑鐐哄垂閸洖绠伴柛婵勫劤閻捇鏌ｉ幋婵愭綗闁逞屽墮閹虫﹢寮崘顔肩＜婵﹩鍘肩粊鍫曟⒒娓氣偓濞佳嗗闂佸搫鍊风欢姘暦閹达箑绠婚柡澶嬪灩缁愮偞绻濋悽闈浶㈤柛濠傜仢閳绘捇顢楅崟顑芥嫼闂佸憡绋戦敃锕傚箠閸曨垱鐓ユ慨妯垮煐閸婄敻鏌涢…鎴濅簽闁告瑢鍋撻柣搴㈩問閸ｎ噣宕板璺虹闁告洦鍨版导鐘绘煕閺囥劌浜滈柡浣靛€曢埞?activate()闂傚倸鍊烽悞锔锯偓绗涘懐鐭欓柟杈鹃檮閸嬪鏌涘☉鍗炵仭妞ゎ偅娲栭湁闁绘挸娴烽幗鐘绘煟閹惧啿鈧鍩€椤掆偓缁犲秹宕曢柆宥呯疇闁归偊鍠楅～?controller/handles闂傚倸鍊烽悞锔锯偓绗涘懐鐭欓柟杈鹃檮閸ゆ劖銇勯弽顐㈠壉闁轰礁顑夊娲敆閳ь剛绮旈悽绋垮惞濠电姴瀚壕鍏笺亜閺冨倸甯堕悽顖樺姂閺屾稓鈧綆鍋呯亸顓熴亜椤撴粌濮傜€规洜鍠栭、鏇㈠Χ閸涱垳小闂傚倸鍊风粈浣革耿闁秴钃熷璺好″☉銏犵妞ゆ棁妗ㄥЧ妤呮偡濠婂嫭顥堟鐐诧工铻栭柛娑卞枛閻у嫭绻濋姀锝嗙【閼煎懏淇婇妶鍌氫壕闂佸疇妫勯ˇ鐢哥嵁濮椻偓椤㈡稑顫濋銏╂闂?濠电姷鏁搁崑鐐哄垂閸洖绠伴柛婵勫劤閻捇鏌熼娑欑凡缂傚秴鍊垮铏规嫚閸欏鏀銈庡亜椤﹂潧鐣峰ú顏勭妞ゆ棁鍋愰崝锕€顪冮妶鍡楃瑐闁煎啿澧庣划濠氬箥椤斿墽锛?
                gizmoManager.setPivot(newPivot);
                gizmoManager.setTargetBox(bounds);
                LOGGER.debug("[SelectionManager] 闂傚倸鍊风粈渚€骞栭鈷氭椽濡舵径瀣槐闂侀潧艌閺呮盯鎷?Gizmo 濠电姷鏁搁崑鐘诲箵椤忓棗绶ら柣锝呯灱閻捇鏌涢锝嗙闁?闂傚倸鍊风粈渚€骞栭锕€鐤柣妤€鐗忕粻楣冩煃瑜滈崜鐔煎蓟濞戙垺鏅查柛銉ュ閸旂顪?: pivot={}", newPivot);
            }
        } catch (Exception e) {
            LOGGER.warn("[SelectionManager] Failed to update gizmo position", e);
        }
    }

    // =====================================================
    // 婵犵妲呴崑鎾跺緤娴犲鑸?expandSelection 闂?闂傚倸鍊搁崐椋庢閿熺姴纾婚柛鏇ㄥ瀬閸ヮ剦鏁嶆繝濞惧亾闁绘帒锕ラ妵鍕冀閵娧呯厒缂佹儳褰炵划娆撳蓟瀹ュ浼犻柛鏇ㄥ亐閸嬫捁銇愰幒鎴犻獓閻熸粎澧楃敮妤呮偂濞戞埃鍋撻獮鍨姎婵炴潙鍊块幃鐐哄礈瑜忕壕鍏肩箾瀹割喕绨介柣锝嗘そ閺岋紕浠﹂懞銉ユ畻閻庢鍠楁刊鐣岀不濞戙垹鍗抽柣鎰問娴?(v3.0)
    // =====================================================
    
    /**
     * 婵犵數濮烽弫鎼佸磻濞戙垹瀚夋い鎺戝€甸崑鎾剁箔濞戞ɑ鍣抽柛瀣崌瀹曞綊顢曢敐鍥吇闂備線鈧偛鑻晶鍙夈亜椤愩埄妲圭紒灞藉船鐓ゆい蹇撴噽閸樻劙姊绘笟鍥у缂佸顕划濠氬Ψ閳哄倻鍘撻柡澶屽仦婵粙宕楃仦鍓х闁稿繐鍚嬮ˉ鍫ユ煛瀹€鈧崰鎾诲焵椤掍胶鈯曟い顓炴处閺呭爼顢楅崟顒傚幐婵炶揪绲介幉锛勬嫻閿涘嫮纾奸弶鍫涘妽瀹曞矂鏌涢埞鎯у⒉闁瑰嘲鎳橀幖褰掑礈閹板吀閭慨濠冩そ瀹曘劍绻濋崘銊ь暡缂傚倷绀侀ˇ顖滅礊婵犲洤鏄ラ柍褜鍓氶妵鍕箳閸℃ぞ澹曟俊鐐€х€靛矂宕归搹顐ゎ洸闁归棿绶￠弫宥夋煟閹邦剙妫?
     * <p>
     * 闂傚倸鍊峰ù鍥Υ閳ь剟鏌涚€ｎ偅灏伴柕鍥у瀵粙濡歌濡插牓姊烘导娆忕槣闁革綇缍佸璇测槈閵忕姷鐫勯梺绋挎湰绾板秹鎮橀崱娑欌拺缂佸娉曠粻鏌ユ煥閺囨ê鐏茬€?
     * 闂?婵?闂傚倷娴囧畷鍨叏閻㈢绀夋俊銈呮噹缁愭鏌熼悧鍫熺凡闁藉啰鍠栭弻锝夊籍閸屾艾浠橀柣鐔哥懕缁犳捇寮婚悢琛″亾閻㈡鐒惧ù鐘崇矒閺屽秹鏌ㄧ€ｎ剙鈷岄梺璇″枟閿曘垽骞冨▎鎴斿亾濞戞瑯鐒芥い锔垮嵆濮婃椽鏌呴悙鑼跺濠⒀屽枟閵囧嫰鏁傜拠鑼化婵?
     * 闂?闂傚倸鍊烽懗鍫曞储瑜旈妴鍐╂償閵忋埄娲稿┑鐘诧工鐎氼參宕ｈ箛娑欑厓闁告繂瀚崳娲煟閹惧啿鈧鍩€椤掆偓缁犲秹宕曢崡鐐嶆稑鈻庨幘鏉戜簵闂侀€炲苯澧撮柡宀嬬秮閹晜娼忛埡鍐憣缂傚倷娴囨ご绋棵洪悢鐓庣畺?Bounding Box
     * 闂?闂傚倸鍊烽懗鍫曗€﹂崼銏″床闁圭儤顨呴崒銊ф喐閺冨牄鈧?BoxSelectTool_v3 濠?GizmoRenderer
     * 闂?闂傚倷娴囬褎顨ョ粙鍖¤€块梺顒€绉寸壕濠氭煟閹邦剚鈻曟繛鎾愁煼閺屾稓浠﹂幆褍鏋欑紓浣瑰敾缂嶄線寮诲☉銏犲嵆闁靛鍎查悵顔尖攽閻愭彃鎮戞俊顐㈠暣瀵鈽夐姀鐘殿唺濠德板€撻悞锕傚汲閻樼粯鈷戦柛婵嗗鐎氫即鏌ㄩ弴銊ら偗鐎殿噮鍋勯鍏煎緞婵犲洤鏁归梻渚€娼чˇ浠嬪窗濡も偓閳绘捇濡搁埡鍌楁嫼婵炴潙鍚嬮悷褏绮欐繝姘厽闁绘棁顔婇崥顐ｄ繆?
     * 
     * @param axis  闂傚倸鍊风粈渚€骞栭锕€纾瑰┑鐘宠壘缁犵娀鏌熼幑鎰靛殭缂佺姵鐗楃换婵囩節閸屾凹浠炬繝娈垮枟缁挸顕ｉ崼鏇為唶婵犻潧妫岄幐鍐磽娴ｆ彃浜鹃梺?Y/Z闂?
     * @param delta 濠电姷鏁告慨顓㈠箯閸愵喖宸濇い鎾寸箖椤洟姊绘笟鈧鑽ゅ緤閹屾富濞寸姴顑呴弸?0 闂傚倸鍊风粈浣革耿闁秲鈧倹绂掔€ｎ亞鏌у┑鐐村灟閸ㄦ椽宕曢幘鍨涘亾楠炲灝鍔氭い锔垮嵆瀵?0 闂傚倸鍊峰ù鍥Υ閳ь剟鏌涚€ｎ偅宕岄柡灞剧洴楠炴﹢鎳犵捄鍝勫腐缂傚倷娴囨ご绋跨暆閹间礁钃?
     */
    public void expandSelection(Axis axis, int delta) {
        expandSelection(axis, true, delta);
    }

    public void expandSelection(Axis axis, boolean isMaxFace, int delta) {
        if (selectionSet.isEmpty()) {
            LOGGER.warn("[SelectionManager] Cannot resize selection: selection is empty");
            return;
        }

        Box bounds = getCurrentBounds();
        if (bounds == null) {
            LOGGER.warn("[SelectionManager] Cannot resize selection: bounds are unavailable");
            return;
        }

        int minX = (int) bounds.minX;
        int minY = (int) bounds.minY;
        int minZ = (int) bounds.minZ;
        int maxX = (int) bounds.maxX - 1;
        int maxY = (int) bounds.maxY - 1;
        int maxZ = (int) bounds.maxZ - 1;

        emptySelectionBounds = null;

        switch (axis) {
            case X -> resizeAlongX(minX, maxX, minY, maxY, minZ, maxZ, isMaxFace, delta);
            case Y -> resizeAlongY(minX, maxX, minY, maxY, minZ, maxZ, isMaxFace, delta);
            case Z -> resizeAlongZ(minX, maxX, minY, maxY, minZ, maxZ, isMaxFace, delta);
        }

        LOGGER.info("[SelectionManager] {}-face resize axis={} delta={} size={}",
            isMaxFace ? "max" : "min", axis.name(), delta, selectionSet.size());

        notifySelectionChanged();

        if (autoPreviewEnabled) {
            Box newBounds = getCurrentBounds();
            if (newBounds != null) {
                try {
                    ToolPreviewManager.getInstance().setPreviewBox(newBounds);
                } catch (Exception e) {
                    LOGGER.debug("[SelectionManager] Failed to update preview box after resize: {}", e.getMessage());
                }
            }
        }
    }

    private void resizeAlongX(int minX, int maxX, int minY, int maxY, int minZ, int maxZ, boolean isMaxFace, int delta) {
        if (delta == 0) return;
        if (isMaxFace) {
            if (delta > 0) {
                for (int x = maxX + 1; x <= maxX + delta; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            selectionSet.add(new BlockPos(x, y, z));
                        }
                    }
                }
            } else {
                for (int x = maxX; x > maxX + delta; x--) {
                    final int removeX = x;
                    selectionSet.removeIf(p -> p.getX() == removeX);
                }
            }
            return;
        }

        if (delta > 0) {
            for (int x = minX; x < minX + delta; x++) {
                final int removeX = x;
                selectionSet.removeIf(p -> p.getX() == removeX);
            }
        } else {
            for (int x = minX - 1; x >= minX + delta; x--) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        selectionSet.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    private void resizeAlongY(int minX, int maxX, int minY, int maxY, int minZ, int maxZ, boolean isMaxFace, int delta) {
        if (delta == 0) return;
        if (isMaxFace) {
            if (delta > 0) {
                for (int y = maxY + 1; y <= maxY + delta; y++) {
                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            selectionSet.add(new BlockPos(x, y, z));
                        }
                    }
                }
            } else {
                for (int y = maxY; y > maxY + delta; y--) {
                    final int removeY = y;
                    selectionSet.removeIf(p -> p.getY() == removeY);
                }
            }
            return;
        }

        if (delta > 0) {
            for (int y = minY; y < minY + delta; y++) {
                final int removeY = y;
                selectionSet.removeIf(p -> p.getY() == removeY);
            }
        } else {
            for (int y = minY - 1; y >= minY + delta; y--) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        selectionSet.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    private void resizeAlongZ(int minX, int maxX, int minY, int maxY, int minZ, int maxZ, boolean isMaxFace, int delta) {
        if (delta == 0) return;
        if (isMaxFace) {
            if (delta > 0) {
                for (int z = maxZ + 1; z <= maxZ + delta; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            selectionSet.add(new BlockPos(x, y, z));
                        }
                    }
                }
            } else {
                for (int z = maxZ; z > maxZ + delta; z--) {
                    final int removeZ = z;
                    selectionSet.removeIf(p -> p.getZ() == removeZ);
                }
            }
            return;
        }

        if (delta > 0) {
            for (int z = minZ; z < minZ + delta; z++) {
                final int removeZ = z;
                selectionSet.removeIf(p -> p.getZ() == removeZ);
            }
        } else {
            for (int z = minZ - 1; z >= minZ + delta; z--) {
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        selectionSet.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    /**
     * Offset the current selection.
     */
    public void offsetSelection(BlockPos offset) {
        if (selectionSet.isEmpty()) {
            LOGGER.debug("[SelectionManager] Selection is empty, skipping offset");
            return;
        }

        Set<BlockPos> moved = new HashSet<>();

        for (BlockPos pos : selectionSet.asUnmodifiableSet()) {
            moved.add(pos.add(offset));
        }

        selectionSet.clear();
        selectionSet.addAll(moved);

        LOGGER.debug("[SelectionManager] Offset selection offset={} size={}", offset, selectionSet.size());

        notifySelectionChanged();
        updateGizmoPosition();

        if (autoPreviewEnabled) {
            Box newBounds = getCurrentBounds();
            if (newBounds != null) {
                try {
                    ToolPreviewManager.getInstance().setPreviewBox(newBounds);
                } catch (Exception e) {
                    LOGGER.debug("[SelectionManager] Failed to update preview box after offset: {}", e.getMessage());
                }
            }
        }
    }

    // =====================================================
    // 婵犵妲呴崑鎾跺緤娴犲鐤い鏍仜缁?getCurrentBounds 闂?闂備浇宕甸崰鎰垝鎼淬垺娅犳俊銈呮噹缁犱即鏌涘☉姗堟敾婵炲懐濞€閺岋絽螣閼测晛绗￠梺缁樺笩椤曆団€︾捄銊﹀磯闁绘碍娼欐导鎰版⒒閸屾碍鍣洪柨鏇樺灲瀵鈽夐姀鐘插敤閻熸粌閰ｅ畷宕囨喆閸曨厾顔曢梺鍦帛鐢宕戦妷鈺傜厵?(v3.1)
    // =====================================================
    
    /**
     * 闂傚倸鍊风粈渚€宕ョ€ｎ喖纾块柟鎯版鎼村﹪鏌ら懝鎵牚濞存粌缍婇弻娑㈠Ψ椤旇崵鏆楁繛瀛樼矊缂嶅﹪寮婚悢鐓庣畾鐟滃秹寮虫潏銊ｄ簻闁靛牆鎳忛崵鍥ㄦ叏婵犲啯銇濇い銏℃礋婵偓闁冲搫鍊愰敃鍌涚厽闊洦鎸荤粋瀣煕閵娿儳鍩ｆ鐐叉瀹曟﹢顢欓懖鈺婃Ч婵＄偑鍊栭崝鎴﹀垂鐠囧弬鐔哥瑹閳ь剙顫忓ú顏勭闁绘劖褰冩慨搴☆渻閵堝棙澶勯柛妯垮亹閸欏懘姊洪崨濠勨槈闁宦板姂瀵煡顢楅崟顒傚幈闂佹寧妫佹慨銈夊极缁烘娊姊?
     * <p>
     * 闂傚倸鍊烽悞锕€顪冮崹顕呯劷闁秆勵殔缁€澶愭倵閿濆骸澧插┑顔挎珪閵囧嫰骞掗幋婵愪痪闂?
     * - Gizmo 缂傚倸鍊搁崐椋庢閿熺姴鍨傞梻鍫熺〒閺嗭箓鏌ｉ姀銈嗘锭闁搞劍绻冪换娑橆啅椤旇崵鍑归梺鎼炲€栧濠氬焵椤掑倹鍤€閻庢凹鍘奸…鍨熼悡搴ｇ瓘闂佺鍕垫畷闁抽攱鍨堕妵鍕箛閳轰胶鍔村┑鈥冲级閹倿骞冨Ο璺ㄧ杸闁挎繂鎳愭禒顓炩攽閻愰潧甯剁紒缁樏悾鐑芥倻缁涘鏅ｉ梺缁樻煥閹芥粏銇?
     * - SelectionRenderer 濠电姷顣藉Σ鍛村磻閸涱収鐔嗘俊顖氱毞閸嬫挸顫濋悡搴ｄ桓闂佹寧绻勯崑娑㈩敇婵傜妞藉ù锝呮贡椤╊偊姊绘担铏广€婇柛鎾寸箞閵嗗啴宕卞☉妯硷紮闁哄鐗勯崝宥夊矗?
     * - 闂傚倸鍊烽懗鍫曗€﹂崼銏″床闁瑰瓨绻愰懓鍧楁煕閿旇骞栭柛銊︾箖閵囧嫰骞掗幋婵愪痪濡炪們鍎卞Λ婵嬪蓟瀹ュ牜妾ㄩ梺鍛婃尰閻╊垶鐛径鎰亱闁割偁鍨婚幊婵嬫⒑闁偛鑻晶鎾煕閳规儳浜炬俊鐐€栧濠氬磻閹炬番浜滈柨鏃囶嚙閻忥絽霉濠婂嫭鍊愭い銏＄☉閳藉鈻庨幋鐘樸倝姊绘笟鈧鑽も偓闈涚焸瀹曘垽鎸婃径鍡樼€洪梺鍝勬储閸ㄦ椽鎮￠悢鍏肩厪濠电偛鐏濋崝姘亜韫囧﹥娅呴柍瑙勫灴椤㈡瑩鎮剧仦鎯у灡闂?
     * 
     * @return 闂備浇宕甸崰鎰垝鎼淬垺娅犳俊銈呮噹缁犱即鏌涘☉姗堟敾婵炲懐濞€閺岋絽螣閼测晛绗￠梺缁樺笩椤曆団€︾捄銊﹀磯闁绘碍娼欐导鎰版⒒閸屾碍鍣洪柨鏇樺灲瀵鏁愭径濞⑩晠鏌曟径鍫濆姶濞寸姴銈搁弻锝嗘償閿涘嫪绮甸梺鎸庢磸閸ㄥ綊鎮鹃悜钘夌濞达絾褰冩禍楣冩⒒閸喓鈯曢柛鐔哄仧閻ヮ亞绱掗～顓㈠仐闂佸搫琚崝宀勫煡婢跺á鐔煎垂椤曞懍绱斿┑锛勫亼閸婃劙寮鍫濈倞鐟滃秹宕甸幋婵冩斀闁绘顕滃銉╂煟濡も偓閸熸潙鐣烽幋锕€绠婚柡澶嬪灩缁愮偞绻濋悽闈浶㈤柛濠傜仢閳绘捇顢楁担铏诡啎闂佺懓顕崑鐐茬暦閸曨剛绠鹃柛婊冨暟缁夘噣鏌涢埡鍐ㄤ槐鐎殿喕绮欓、姗€鎮欏▓鎸幮㈠┑鐘垫暩閸嬫稑螞濞嗘垶鏆滈柣鎰暩娑撳秹鏌＄仦璇插姕闁?null
     */
    public Box getCurrentBounds() {
        if (currentTool instanceof BoxSelectTool && emptySelectionBounds != null) {
            return copyBox(emptySelectionBounds);
        }
        if (selectionSet.isEmpty()) {
            return emptySelectionBounds != null ? copyBox(emptySelectionBounds) : null;
        }
        
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (BlockPos pos : selectionSet.asUnmodifiableSet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        
        // 闂傚倷绀侀幖顐λ囬锕€鐤炬繝濠傜墕閽冪喖鏌曟繛鍨壄?Box闂傚倸鍊烽悞锔锯偓绗涘懐鐭欓柟杈鹃檮閸嬪鏌涢埄鍐喛闁稿鎹囧畷褰掝敃椤掑啠鍋撶捄銊㈠亾?min 闂?max+1 闂傚倸鍊烽悞锕傛儑瑜版帒绀夌€光偓閳ь剟鍩€椤掍礁鍤柛銊﹀▕閸┾偓妞ゆ巻鍋撻柣蹇斿哺閹囨偐閼碱剚娈鹃梺鑲╊焾閻忔氨绱為崶顒佺厱闁圭偓顨呴幏瀣ｉ悜鑺モ拻濞达綀顫夐崑鐘绘煕鎼淬倖鐝柟渚垮妼閻ｇ兘宕堕妸銉紩?BlockPos 闂傚倸鍊风粈渚€骞栭銈傚亾濮樺崬鍘寸€规洖缍婇弻鍡楊吋閸涱喛绶㈤梻浣瑰缁诲倿藝椤栫偛鍑犲〒姘ｅ亾闁哄备鍓濆鍕偓锝庝憾閸ゅ绱撻崒姘毙㈢紒缁橈耿瀵鍨鹃幇浣告倯闂佸憡渚楅崰姘跺Χ椤愩倗纾?
        return new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }
}
