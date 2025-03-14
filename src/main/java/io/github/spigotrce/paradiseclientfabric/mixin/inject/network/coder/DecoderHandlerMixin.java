package io.github.spigotrce.paradiseclientfabric.mixin.inject.network.coder;

import com.mojang.logging.LogUtils;
import io.github.spigotrce.paradiseclientfabric.Helper;
import io.github.spigotrce.paradiseclientfabric.ParadiseClient_Fabric;
import io.github.spigotrce.paradiseclientfabric.event.channel.PluginMessageEvent;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.handler.DecoderHandler;
import net.minecraft.network.handler.NetworkStateTransitionHandler;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PacketType;
import net.minecraft.util.profiling.jfr.FlightProfiler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DecoderHandler.class)
public class DecoderHandlerMixin <T extends PacketListener> {
    @Shadow
    private static final Logger LOGGER = LogUtils.getLogger();
    @Mutable
    @Final
    @Shadow
    private final NetworkState<T> state;

    @SuppressWarnings("unused")
    public DecoderHandlerMixin(NetworkState<T> state) {
        this.state = state;
    }

    @Inject(method = "decode", at = @At("HEAD"), cancellable = true)
    public void decode(ChannelHandlerContext context, ByteBuf buf, List<Object> objects, CallbackInfo ci) {
        PacketByteBuf b = new PacketByteBuf(buf.copy());
        if (b.readVarInt() == 25) {
            PluginMessageEvent event = new PluginMessageEvent(b.readString(), b);
            try {
                ParadiseClient_Fabric.getEventManager().fireEvent(event);
            } catch (Exception e) {
                LOGGER.error("Unable to fire PluginMessageEvent", e);
                LOGGER.error("Not dropping the packet! (TODO: Change this in the future)");
                return;
            }

            if (event.isCancel()) return;
        }

        int i = buf.readableBytes();
        if (i != 0) {
            Packet<? super T> packet = this.state.codec().decode(buf);
            PacketType<? extends Packet<? super T>> packetType = packet.getPacketId();
            FlightProfiler.INSTANCE.onPacketReceived(this.state.id(), packetType, context.channel().remoteAddress(), i);
            if (buf.readableBytes() > 0) {
                String var10002 = this.state.id().getId();
                Helper.printChatMessage("&cError handling packet " + var10002 + "/" + packetType + " (" + packet.getClass().getSimpleName() + ") was larger than I expected, found " + buf.readableBytes() + " bytes extra whilst reading packet " + packetType);
//                throw new IOException("Packet " + var10002 + "/" + packetType + " (" + packet.getClass().getSimpleName() + ") was larger than I expected, found " + buf.readableBytes() + " bytes extra whilst reading packet " + packetType);
            } else {
                objects.add(packet);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(ClientConnection.PACKET_RECEIVED_MARKER, " IN: [{}:{}] {} -> {} bytes", this.state.id().getId(), packetType, packet.getClass().getName(), i);
                }

                NetworkStateTransitionHandler.onDecoded(context, packet);
            }
        }
        ci.cancel();
    }
}
