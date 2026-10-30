package com.director.export.video;

import net.minecraft.client.texture.NativeImage;
import java.io.IOException;

/** One PNG, published only after capture and writing complete successfully. */
final class PngStillEncoder implements VideoEncoder {
    private StagedVideoOutput output;
    private boolean written;
    @Override public void init(VideoExportSettings settings,int frames) throws IOException {
        if(output!=null || frames!=1 || settings==null || !settings.validate()
                || settings.format!=VideoExportSettings.FORMAT_PNG_STILL) throw new IOException("静帧导出设置无效");
        output=StagedVideoOutput.still(settings.getOutputPathAsPath());
    }
    @Override public void encodeFrame(NativeImage image,int index) throws IOException {
        if(output==null || written || index!=0 || image==null) throw new IOException("静帧仅接受一帧");
        image.writeTo(output.path().toFile());written=true;
    }
    @Override public void finish() throws IOException {
        if(output==null || !written) throw new IOException("静帧尚未写入");
        output.commit();
    }
    @Override public void abort() {
        if(output!=null) try {output.close();}
        catch(IOException failure) {org.slf4j.LoggerFactory.getLogger(PngStillEncoder.class).warn("静帧临时文件清理失败",failure);}
    }
}
