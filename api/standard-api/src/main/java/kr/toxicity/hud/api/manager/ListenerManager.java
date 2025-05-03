package kr.toxicity.hud.api.manager;

import kr.toxicity.hud.api.listener.HudListener;
import kr.toxicity.hud.api.update.UpdateEvent;
import kr.toxicity.hud.api.yaml.YamlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;
import java.util.function.Function;

/**
 * Represents listener manager.
 */
public interface ListenerManager {
    /**
     * Adds listener builder.
     * @param name listener name
     * @param listenerFunction builder
     */
    void addListener(@NotNull String name, @NotNull Function<YamlObject, Function<UpdateEvent, HudListener>> listenerFunction);

    /**
     * Gets all listener names
     * @return listener name
     */
    @NotNull
    @Unmodifiable
    Set<String> getAllListenerKeys();
}
