package com.director.client.gizmo.logic;

import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionSet;
import com.director.client.gizmo.Axis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 濠电姷顣藉Σ鍛村磻閹捐泛绶ゅù鐘差儏閼?TransformApplier - 闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁撻悩鍐蹭画闂佹寧娲戦悞锕傚箠瀹€鍕厽閹兼番鍊ゅ鎰箾閸欏澧い鏇秮瀹曞ジ寮撮悙鐢垫毎闂備礁鎼崯顐︽偋閸℃瑧灏电€广儱顦伴悡鏇㈡倶閻愭彃鈷旈柍顖涙礃缁绘盯宕煎☉妯间紙濠?
 * 
 * 闂?Gizmo 闂傚倸鍊搁崐鐑芥倿閿曞倹鍎戠憸鐗堝笒缁€澶屸偓鍏夊亾闁逞屽墴閸┾偓妞ゆ帊绀侀崵顒勬煕濞嗗繐鏆ｉ柣娑卞櫍瀵粙顢橀悢鍝勫汲闂佽鍑界紞鍡涘磻閸曨垼鏁侀柟鐐灱閺€浠嬫煟濡鍤嬬€规悶鍎甸弻鐔兼惞椤愩垹顫岄梺宕囩帛濡啯鎱ㄩ埀顒勬煏閸繃顥犻柛妯绘倐閺岋絾鎯旈婊呅ｉ梺绋款儏濡繈骞嗙仦鍓х瘈闁搞儯鍔庨崢閬嶆⒑缂佹ɑ鐓ラ柟娴嬧偓宕囨殾鐎光偓閳ь剟鍩€椤掑喚娼愭繛鍙夌墵閹儲绺界粙璺ㄧ暫濠电偛妫欓幐鎼佸垂閸屾稏浜滈柟鎵虫櫅閳ь剚顨呴悾鐑藉醇閺囩啿鎷绘繛杈剧到閹诧繝骞嗛崼銏㈢＜濠㈣泛顑嗙紞鎴︽婢舵劖鐓曢柡鍥ュ妼楠炴銇?
 *  - 闂傚倸鍊搁崐鎼佸磹妞嬪孩顐介柨鐔哄Т绾惧鏌涢弴銊ョ€柛銉墻閺佸秵绻濇繛鎯т壕闂?(Selection Bounds)
 *  - 闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁撻悩鑼舵憰闂佹寧绻傞ˇ顖滅矆婢舵劕绠规繛锝庡墮婵″ジ鏌?Actor Transform (闂傚倸鍊搁崐椋庣矆娓氣偓楠炴牠顢曢敂钘変罕濠电姴锕ょ€氼噣銆呴崣澶岀瘈闂傚牊绋掗敍宥夋煟閹哄秶鐭欓柡宀嬬秮婵偓闁靛繆鏅濋崝鍝ョ磽娴ｅ搫袨闁稿海鏁诲璇差吋閸℃ê顫￠梺鐟板槻閼活垶宕㈤垾鏂ユ斀?
 * 
 * 闂傚倸鍊搁崐宄懊归崶顒€违闁逞屽墴閺屾稓鈧綆鍋呯亸浼存煏閸パ冾伃鐎殿喕绮欐俊姝岊槷婵℃彃鐗撳鐑樺濞嗗繒妲ｉ梺闈╃秶缂嶄礁顕ｆ繝姘亜闁绘挸楠搁悗顓烆渻閵堝棙鈷愰悗姘叄閺佸秹宕熼鐙呯闯闁诲骸绠嶉崕鍗炍涘▎鎾嶅宕奸妷锔惧幐?
 *  - 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柣鎴ｆ绾剧粯绻涢幋鐐跺妞ゃ儲纰嶉妵鍕箻鐎靛摜鐣奸梺鍝勫閸婂灝顫忔繝姘＜婵炲棙鍨肩粣妤呮⒑缁嬫鍎忛悗姘濡喎顪冮妶鍡欏缂侇喖鐗撻幃?
 *  - 闂傚倸鍊峰ù鍥х暦閸偅鍙忛柟缁㈠櫘閺佸嫰鏌涘☉娆愮稇闁汇値鍠楅妵鍕冀椤愵澀绮跺┑顔款潐閻擄繝寮婚妸銉㈡斀闁糕檧鏅滄晥闂備胶绮敮鐔碱敋瑜旈垾锔炬崉閵婏箑纾梺缁樼濞兼瑦瀵奸幇鐗堚拻闁稿本纰嶉崳娲煥閺囨ê鐏╂い鏇秮瀹曞ジ寮撮悙鍝勬暪婵犵數濞€濞佳兠洪埡鍐╁厹闁逞屽墴濮婄粯鎷呴崨濠冨創闂佺懓鍢查澶婄暦閹达箑宸濇い鎾楀啯顔傞梻浣告啞濞诧箓宕抽鍢夛絾绻濆顓涙嫼?
 *  - 闂傚倸鍊风粈浣革耿闁秴鍌ㄧ憸鏃堝箖濞差亜惟闁靛鍠楃紞搴㈢節閻㈤潧校闁煎綊绠栧鍛婄瑹閳ь剟寮婚弴銏犻唶婵犲灚鍔栫瑧缂傚倷绶￠崑鍕矓瑜版帒绠栨俊銈呮噺閺呮煡骞栫划鍏夊亾瀹曞浂鍟堢紓鍌氬€峰鎺旀閿熺姴闂柨婵嗩槸閽冪喖鏌曟繛鍨姉婵℃彃鐗婃穱濠囶敍濠婂啫浠橀梺鎰佷簻瀹曨剟鍩為幋锔藉€烽悹楦挎婢跺嫰姊洪崨濠庢當闁哥喎娼￠幃鎯х暋闁妇鍙嗛梺鍛婂姈閸庢娊骞冮幋锔解拺闁告稑锕ユ径鍕煕濮椻偓缁犳牠骞冮敓鐘茬＜闁绘劕顕崢浠嬫⒑缂佹﹩鐒惧ù婊庡墴瀹曟繂顓奸崱娆戠槇闂侀潧绻嗗Σ鍛存倿閻ｅ瞼纾奸弶鍫氭櫅娴犳粍銇勯幘鐐藉仮鐎规洖鐖兼俊鎼佸Ψ閿旈敮鍋撻銏♀拻濞达絽鎲＄拹锟犳煕鎼存稑鈧繂鐣疯ぐ鎺撳仺闁告挸寮堕弲鈺傜節閻㈤潧孝婵炶绠撻幃锟犲Ψ閿斿墽顔曢梺鐟邦嚟娴兼繈顢旈崼娑掑亾閸愩劉鏋庨柟瀵稿Х閿涙粌鈹戞幊閸婃劙宕戦幘娣簻闁挎梻鍋撻弳顒侇殽閻愬弶顥㈢€规洜鍠栭、妯衡槈濡懓顥氭繝鐢靛仦閸ㄥ爼顢旈鍫晜闁告侗鍘剧粣鐐烘倵楠炲灝鍔氭い锔垮嵆閹€斥槈閵忥紕鍘遍梺瑙勫閺佹悂宕㈠☉銏＄厱閹兼番鍨虹亸鎵磼缂佹娲寸€殿喖鐖奸獮瀣倻閸℃﹫绱楅梺璇插椤旀牠宕抽鈧畷婊堟偄閻撳海鐣洪梺缁樺灱婵倝宕戠€ｎ喗鐓曟い鎰剁悼缁犳垶绻涢敐搴℃珝婵?
 * 
 * 濠电姷鏁告慨鐑藉极閹间礁纾绘繛鎴旀嚍閸ヮ剦鏁囬柕蹇曞Х椤︻噣鎮楅獮鍨姎妞わ富鍨崇划鍫ュ醇濠㈡繂缍婇幃鈩冩償閿濆棙鍠栭梻?
 * - Minecraft 闂傚倸鍊搁崐椋庣矆娓氣偓楠炴牠顢曢敃鈧壕鐟扳攽閻樺疇澹樼紒鐘靛█閺岀喖鎮欓鈧晶顖炴煟閹哄秶鐭欓柡灞界Ч閸┾剝鎷呴崨濠冾啀闂備礁纾划顖氼潖婵犳艾鐒垫い鎺嗗亾缂佺姴绉瑰畷鏇㈡焼瀹ュ懐鐤囬柟鑹版彧缁茬偓鍒婃總鍛婄厸閻忕偠顕ч崝婊堟煟閹惧娲撮柡灞剧洴楠炲洭鎮介棃娑樺紬闂備胶顭堢€涒晠骞愰懡銈嗗床婵炴垯鍨圭痪褔鏌熺€电鍓辨俊顐畵濮婄儤娼幍顕呮М缂備礁顦遍幊鎾绘偩闁垮闄勭紒瀣嚦閵娾晜鐓ラ柡鍐ｅ亾闁稿骸纾槐鐐村緞閹邦厸鎷婚梺绋挎湰閼归箖鍩€椤掍焦鍊愰柟顔ㄥ棛鐤€婵炴垶顭囬敍娑㈡⒑缂佹ê濮囬柟纰卞亰閹锋垿鎮㈤崗鑲╁幗闂佸搫鍟崐鍝ユ暜閸洘鍋℃繛鍡樼懅閻ｇ敻鏌″畝鈧崰鎾诲焵椤掑倹鏆╂い顓炵墛缁傛帡鏁冮崒娑氬幗闂佽鍎抽顓㈠箠閸ヮ剚鐓涢悘鐐插⒔椤吋銇勯敃浣圭彧闁靛洤瀚版俊鎼佸Ψ閿旂粯锛嗛柣?NBT 闂傚倸鍊搁崐鐑芥嚄閸洏鈧焦绻濋崶銊ュ壒闂佹悶鍎洪崜娆戠矆閸愵喗鐓忓┑鐐戝啫鏆欑紒鐘冲灴濮?
 * - 闂傚倸鍊搁崐椋庣矆娓氣偓楠炴牠顢曢敃鈧悿顔姐亜閹拌泛鐦滈柡浣稿閺屾盯顢曢敐鍥┛閻熸粎澧楃敮鎺楃嵁閵忥紕绠鹃柟瀵稿剱閻掕姤銇勯弮鈧幑鍥箖濡ゅ懐宓侀柛顭戝枛婵酣鏌ｆ惔銏犲毈闁哥姵姘ㄩ崚鎺戔枎閹邦喚鐦堥梺绋挎湰椤ㄥ棝锝炲澶嬧拺閻熸瑥瀚崝銈夋煟鎺抽崝鎴︽晲閻愬弬鏃€鎷呴崗鍝ョ泿闂備礁鍟块幖顐︻敄閸ヮ剚鍋╅柣銏犳啞閻撴瑦銇勯弮鍥у惞闁活厽鐟ラ埞鎴﹀焺閸愨晛鈧劕鈹戦垾宕囧煟鐎规洖鐖奸、鏃堝礋閸偒妫ㄩ梻鍌氬€搁崐椋庣矆娓氣偓楠炲鏁撻悩鑼舵憰闂佹寧绻傞ˇ顖滅矆婢舵劕绠规繛锝庡墮婵″ジ鏌＄€ｂ晝绐旈柡灞剧洴楠炴ê螖閳ь剟骞婇幇鐗堟櫖婵犻潧顑嗛埛鎴︽煕濠靛棗顏柍璇差樀閺屾盯骞樼€垫悶鈧帡鏌℃笟鍥ф珝鐎规洖宕灒闁革富鍘鹃悾鐐繆閻愵亜鈧牠鎮уΔ鍛︽繛鎴欏灩绾惧鏌熼幆褏锛嶉柡鍡畵閺屾盯濡烽姀鈩冪彟闂佽　鍋撻柕濞炬櫆閳锋帒霉閿濆洦鍤€妞ゆ洘绮庣槐鎺斺偓锝庡亜閻忔挳鏌熼姘拱缂佹鍠栭崺鈧い鎺戝閳ь兛绀侀～婊堝焵椤掆偓閻ｇ兘顢曢敃鈧敮閻熸粌鐬肩划顓熷緞婵炵偓鏂€闁圭儤濞婂畷鎰板箻缂佹ê鈧潡鏌ㄩ弴鐐扮椽濠㈣泛澶囬崑鎾绘晲鎼粹剝鐏堢紓浣哄珡閸ャ劎鍘介梺鐟扮摠缁诲啴藟濠婂喚鐔嗙憸蹇旀櫠濡ゅ啯宕叉繛鎴欏灮閸欐挻銇勯幘璺轰粶缂佷緡鍣ｉ幃妤€鈻撻崹顔界亞缂備緡鍠楅悷銉╂偩閻戣棄閱囬柡鍥ュ妽閺呮粍绻涙潏鍓хМ闁哄懎寮剁粩鐔煎即閵忊€斥偓鐢告煕韫囨搩妲稿ù婊堢畺濮婃椽宕ㄦ繝鍐槱闂佸憡鎸婚悷鈺呮晲閻愮儤鏅搁柣妯绘灱閹锋椽姊洪崨濠勨槈妞ゎ収鍓欓埢鎾斥枎閹邦喚顔曢梺鍛婁緱閸犳绮堥埀顒€顪冮妶搴濈盎闁哥喐娼欓悾鐑藉箳濡や礁鈧兘鏌ょ喊鍗炲婵炵厧顦靛铏规嫚閹绘帒姣愮紓鍌氱Т濡繂鐣烽姀銈嗗仼鐎光偓閳ь剟鎯岄崼鐔翠簻闁哄洦顨呮禍楣冩⒑閸濆嫮鐒跨紒缁樼箓閻ｇ兘骞掗幋顓犲弳闂佸憡娲﹂崑鍛存偩椤掍胶绡€?
 * 
 * @author Director
 * @version 1.0
 */
public class TransformApplier {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformApplier.class);
    
    private final SelectionManager selectionManager;
    
    // 闂傚倸鍊搁崐椋庣矆娴ｈ櫣绀婂┑鐘叉搐缂佲晠姊洪鈧粔鐢稿磻鐎ｎ偂绻嗛柕鍫濇噹閺嗗﹪鏌涚€ｎ偅灏い顐ｇ箞椤㈡﹢鎮滈崱妤佺杺濠电姷鏁搁崑鐔煎储瑜旈垾锕€鐣￠幍顔芥闂佸壊鍋呭ú鏍偂濮椻偓閺岋綁骞囬棃娑橆潾闂佸憡蓱閸庢娊鍩為幋锔藉€烽柡澶嬪灩娴犳悂姊洪悷鎵癁闁哄懐濮撮悾鐑藉鎺抽崑鍛存煕閹般劍娅呭ù鐘冲劤椤啴濡堕崱妯煎弳婵犫拃鍌滅煓闁诡垰鐭傞獮妯呵庨璺ㄧ泿闂備礁鎼粔鏌ュ礉鎼淬劍鍎楅悗锝庡枟閻撶喖鏌ㄥ┑鍡╂Х闁规煡绠栭弻鐔碱敊缁涘鍔哥紓浣哄У閻╊垶鐛鈧畷婊勭瑹閸パ勬毉闂傚倸鍊风粈渚€骞栭鈶芥稑鈽夐姀鐘电崶闂佸搫绋侀崢濂告嫅閻斿吋鐓曟い鎰Т閸旀岸骞嗛悢鍏尖拺闂傚牊渚楀Σ鍫曟煕鎼淬垹鈻曢柛鈹惧亾濡炪倖宸婚崑鎾淬亜椤撶姴鍘寸€殿喖顭锋俊鎼佸Ψ閵忊槅娼旀繝纰樻閸ㄧ敻宕戦幇鏉跨妞ゆ牗绋撶粻楣冩煛婢跺鐏╂慨锝囧仱閺岋繝宕ㄩ姣裤垽鏌ｉ敐鍛Щ閾绘牠鏌嶈閸撶喖銆佸Ο鑽ら檮缂佸鐏濋懓鍨攽閻愬弶顥為柛鎿冨亰瀵粙顢橀悢鍝勫箺闂備線娼х换鍡涘疾濞戙垺鍊靛┑鐘崇閻撴洟鏌嶉埡浣告灓婵炲牊锚闇夋繝濠傚閻帗鎱ㄦ繝鍛仩缂侇喗鐟ч幑鍕Ω瑜忛弶浠嬫⒒娴ｄ警鐒鹃梺甯稻缁傚秹宕奸弴妯峰亾娴ｅ壊娼╅柤鍝ユ暩閸斿爼鎮楅獮鍨姎婵☆偅顨呴埢?0 婵犵數濮烽弫鎼佸磻閻樿绠垫い蹇撴缁躲倝鏌ｉ敐鍛伇闁活厽鎹囬弻锟犲醇閵忊剝姣勯梺?
    private double moveCarryX = 0.0;
    private double moveCarryY = 0.0;
    private double moveCarryZ = 0.0;
    private double resizeCarryX = 0.0;
    private double resizeCarryY = 0.0;
    private double resizeCarryZ = 0.0;
    
    // 闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁撻悩鑼舵憰闂佹寧绻傞ˇ顖滅矆婢舵劕绠规繛锝庡墮婵″ジ鏌＄€ｂ晝绐旈柡灞剧洴楠炴ê螖閳ь剟骞婂鍥╃當濠㈣埖鍔栭埛鎴︽煙缁嬫寧鎹ｉ柍钘夘樀閹顫濋悡搴＄睄閻庤娲橀崝娆撳箖濞嗘挻鍊绘俊顖滃帶楠炲牓姊绘担鐟邦嚋缂佽鍊搁湁闁绘垼妫勯崒銊︾節婵犲倹鍣界痪鎯с偢瀵爼宕煎☉妯侯瀷闂佺懓鍟垮ú銊╁焵椤掍緡鍟忛柛鐘冲哺瀵偅绻濆銉㈠亾娓氣偓瀵噣宕煎┑鍡欑崺婵＄偑鍊栧ú宥夊磻閹剧繝绻嗛柛鎾楀啫骞嬪┑顔硷攻濡炶棄鐣烽悜绛嬫晣鐟滃骸袙瀹€鍕拺闁告繂瀚崳娲煟閳哄﹤鐏︽鐐叉瀹曠厧鈹戦幇顒佸€┑鐘灱濞夋盯顢栭崱妞绘瀺?
    // private final AnimationManager animationManager;
    
    public TransformApplier(SelectionManager selectionManager) {
        this.selectionManager = selectionManager;
    }
    
    /**
     * 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柣鎴ｆ绾剧粯绻涢幋鐐跺妞ゃ儲纰嶉妵鍕箻鐎靛摜鐣奸梺鍝勫閸婂灝顫忔繝姘＜婵炲棙鍨肩粣妤呮⒑缁嬫鍎忛悗姘濡喎顪冮妶鍡欏缂侇喖鐗撻幃?
     * 
     * 婵犵數濮烽弫鎼佸磻閻樿绠垫い蹇撴缁€濠囨煃瑜滈崜姘辨崲濞戞瑥绶為悗锝庡亞椤︿即鎮?SelectionManager.offsetSelection() 闂傚倸鍊搁崐椋庣矆娓氣偓楠炴牠顢曢敃鈧壕鐟懊归悩宸劀缂傚秵鐗曢…璺ㄦ崉閻戞ɑ鎷遍梺绋跨箲缁捇寮诲☉銏╂晝闁挎繂妫涢ˇ銊╂⒑閸濆嫭濯奸柛鎾寸洴閸┾偓妞ゆ巻鍋撻柛妯荤矒瀹曟垿骞樼紒妯煎帗閻熸粍绮撳畷婊冣槈閵忕姷顦梺鍦劋椤ㄥ棝宕愮紒妯圭箚妞ゆ牗姘ㄥВ鐐烘煛鐎ｎ偆澧垫慨濠冩そ閺屽懘鎮欓懠璺侯伃婵犫拃鍕唉闁哄矉绱曟禒锕傛嚍閵夈儲顏￠柣搴＄仛濠㈡鈧凹鍘鹃幑銏犫攽鐎ｎ亞锛滈梺缁樺姈閹矂宕銏♀拻闁稿本鐟ㄩ崗宀勬煙閾忣偅宕岄柟顔矫埞鎴﹀醇閵忋垻鍘犻柣搴ゎ潐濞叉牕煤閵娧呯焼濠电姴娲﹂悡娑㈡煕閹扳晛濡垮褎鐩弻锝夊箳閹搭厾鍚嬮梺鍝勭焿缁绘繂鐣烽悢纰辨晝闁靛牆娴傞崬娲⒑?
     * 
     * @param delta 濠电姴鐥夐弶搴撳亾濡や焦鍙忛柣鎴ｆ绾剧粯绻涢幋鐐跺妞ゃ儲纰嶉妵鍕箻鐎靛摜鐣奸梺鍝勫閸婂灝顫忕紒妯诲闁告稑锕ら弳鍫㈢磼閻愵剚绶茬紒澶屾嚀閻ｇ柉銇愰幒鎴︽暅濠德板€愰崑鎾绘⒒?
     */
    public void applyMove(Vec3d delta) {
        SelectionSet set = selectionManager.getSelectionSet();
        
        if (set.isEmpty()) {
            LOGGER.debug("[TransformApplier] Selection is empty, skipping move");
            return;
        }
        
        // 闂?delta 闂傚倸鍊风粈渚€骞栭位鍥焼瀹ュ懐锛熼梺鍦濠㈡绮堥崼鐔稿弿婵妫楁晶浼存煛閸☆厾鐣甸柡灞剧☉閳诲氦绠涢敐鍠般劑姊哄畷鍥╁笡婵☆偄鍟村濠氭晲婢跺﹦顓洪梺缁樺姇濡瑩宕归悽绋跨厺闁规儳顕弧鈧┑顔斤供閸橀箖宕?BlockPos 婵犵數濮烽弫鍛婃叏椤撱垹绠柛鎰靛枛瀹告繃銇勯幘瀵哥畺妞ゎ偅娲熷?
        moveCarryX += delta.x;
        moveCarryY += delta.y;
        moveCarryZ += delta.z;
        
        int stepX = extractStep(moveCarryX);
        int stepY = extractStep(moveCarryY);
        int stepZ = extractStep(moveCarryZ);
        
        if (stepX == 0 && stepY == 0 && stepZ == 0) {
            LOGGER.debug("[TransformApplier] Move delta is below one block, accumulating");
            return;
        }
        
        moveCarryX -= stepX;
        moveCarryY -= stepY;
        moveCarryZ -= stepZ;
        
        LOGGER.debug("[TransformApplier] Offset selection delta=({}, {}, {})", stepX, stepY, stepZ);
        
        selectionManager.offsetSelection(new BlockPos(stepX, stepY, stepZ));
        
        LOGGER.info("[TransformApplier] Selection moved size={}", selectionManager.getSelectionSet().size());
    }
    
    /**
     * Resize the selection along one axis.
     */
    public void applyResize(Axis axis, double delta) {
        applyResize(axis, true, delta);
    }

    public void applyResize(Axis axis, boolean isMaxFace, double delta) {
        SelectionSet set = selectionManager.getSelectionSet();

        if (set.isEmpty()) {
            LOGGER.debug("[TransformApplier] Selection is empty, skipping resize");
            return;
        }

        switch (axis) {
            case X -> resizeCarryX += delta;
            case Y -> resizeCarryY += delta;
            case Z -> resizeCarryZ += delta;
        }

        int deltaInt = switch (axis) {
            case X -> extractStep(resizeCarryX);
            case Y -> extractStep(resizeCarryY);
            case Z -> extractStep(resizeCarryZ);
        };

        if (deltaInt == 0) {
            LOGGER.debug("[TransformApplier] Axis {} delta is below one block, accumulating", axis);
            return;
        }

        switch (axis) {
            case X -> resizeCarryX -= deltaInt;
            case Y -> resizeCarryY -= deltaInt;
            case Z -> resizeCarryZ -= deltaInt;
        }

        LOGGER.debug("[TransformApplier] Resize selection axis={} face={} delta={}",
            axis, isMaxFace ? "max" : "min", deltaInt);
        selectionManager.expandSelection(axis, isMaxFace, deltaInt);

        LOGGER.info("[TransformApplier] Selection resized axis={} face={} delta={} size={}",
            axis, isMaxFace ? "max" : "min", deltaInt, selectionManager.getSelectionSet().size());
    }

    /**
     * Resize the selection from a corner handle.
     */
    public void applyResizeFromCorner(Vec3d delta, net.minecraft.util.math.Vec3i cornerSign) {
        SelectionSet set = selectionManager.getSelectionSet();

        if (set.isEmpty()) {
            return;
        }

        LOGGER.debug("[TransformApplier] Resize selection from corner delta=({}, {}, {})", delta.x, delta.y, delta.z);

        if (Math.abs(delta.x) > 0.1) {
            applyResize(Axis.X, cornerSign.getX() > 0, delta.x);
        }
        if (Math.abs(delta.y) > 0.1) {
            applyResize(Axis.Y, cornerSign.getY() > 0, delta.y);
        }
        if (Math.abs(delta.z) > 0.1) {
            applyResize(Axis.Z, cornerSign.getZ() > 0, delta.z);
        }
    }

    /**
     * Reset accumulated drag deltas.
     */
    public void resetAccumulators() {
        moveCarryX = moveCarryY = moveCarryZ = 0.0;
        resizeCarryX = resizeCarryY = resizeCarryZ = 0.0;
    }

    /** Gesture boundary hooks for domain-specific undo transactions. */
    public void beginDragSession() {}

    public void endDragSession() {}

    private int extractStep(double value) {
        if (value >= 1.0) {
            return (int) Math.floor(value);
        }
        if (value <= -1.0) {
            return (int) Math.ceil(value);
        }
        return 0;
    }

    /**
     * Generic multi-axis resize path.
     */
    public void applyResize(Vec3d axisDelta) {
        LOGGER.debug("[TransformApplier] Generic resize delta=({}, {}, {})", axisDelta.x, axisDelta.y, axisDelta.z);

        SelectionSet set = selectionManager.getSelectionSet();
        if (set.isEmpty()) {
            LOGGER.debug("[TransformApplier] Selection is empty, skipping generic resize");
            return;
        }

        if (Math.abs(axisDelta.x) > 0.01) {
            applyResize(Axis.X, axisDelta.x);
        }
        if (Math.abs(axisDelta.y) > 0.01) {
            applyResize(Axis.Y, axisDelta.y);
        }
        if (Math.abs(axisDelta.z) > 0.01) {
            applyResize(Axis.Z, axisDelta.z);
        }
    }
    public void applyRotation(double angle, Axis axis) {
        double angleDegrees = Math.toDegrees(angle);
        LOGGER.debug("[TransformApplier] Rotate target ({} deg around {})", angleDegrees, axis);

        // TODO: delegate rotation changes to the animation/transform system when available.
    }

    public boolean isAnimationMode() {
        return false;
    }
}

