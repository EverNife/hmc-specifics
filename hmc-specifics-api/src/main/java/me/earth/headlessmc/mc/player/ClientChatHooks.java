package me.earth.headlessmc.mc.player;

import java.lang.reflect.Method;

/**
 * The loader-side hooks a typed chat line passes through before it leaves for the server.
 *
 * <p>The chat GUI runs these; the player entity does not. A headless console sends its lines
 * straight from the player entity, so without this a client-side command typed here would skip its
 * own handler and reach the server as an unknown command.
 *
 * <p>Everything is looked up reflectively because these sources also build the Fabric jar, where
 * none of these classes exist - and the lookups only ever touch classes the loader owns, whose
 * names survive remapping. Off Forge every method here is the identity.
 */
public final class ClientChatHooks {
    private static final Object UNRESOLVED = new Object();

    private static Object sendMessageHook = UNRESOLVED;
    private static Object commandHandler = UNRESOLVED;
    private static Method executeCommand;

    private ClientChatHooks() {
    }

    /**
     * Offers the line to the loader's client-chat event.
     *
     * @param message the line about to be sent.
     * @return the line to send, which a listener may have replaced, or {@code null} if one
     *         cancelled it.
     */
    public static String onSend(String message) {
        Method hook = sendMessageHook();
        if (hook == null) {
            return message;
        }

        try {
            // The event answers an empty string when a listener cancels; every other answer is the
            // message to send, replaced or not.
            String result = (String) hook.invoke(null, message);
            return result == null || result.isEmpty() ? null : result;
        } catch (Throwable t) {
            return message;
        }
    }

    /**
     * Offers the line to the loader's client-side command handler.
     *
     * @param sender  the player the command runs as.
     * @param message the line about to be sent.
     * @return true if the handler ran a command, meaning nothing should reach the server.
     */
    public static boolean consumedAsCommand(Object sender, String message) {
        Object handler = commandHandler();
        if (handler == null) {
            return false;
        }

        try {
            return (Integer) executeCommand.invoke(handler, sender, message) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Method sendMessageHook() {
        if (sendMessageHook == UNRESOLVED) {
            Method found = null;
            try {
                found = Class.forName("net.minecraftforge.event.ForgeEventFactory")
                             .getMethod("onClientSendMessage", String.class);
            } catch (Throwable ignored) {
                // Fabric, or an era whose Forge has no client chat event - nothing to run.
            }

            sendMessageHook = found;
        }

        return (Method) sendMessageHook;
    }

    private static Object commandHandler() {
        if (commandHandler == UNRESOLVED) {
            Object found = null;
            try {
                Class<?> type = Class.forName("net.minecraftforge.client.ClientCommandHandler");
                found = type.getField("instance").get(null);
                // Matched by name and arity: the parameter is a Minecraft type, whose name does not
                // survive remapping the way the loader's own names do.
                for (Method method : type.getMethods()) {
                    if ("executeCommand".equals(method.getName())
                            && method.getParameterTypes().length == 2) {
                        executeCommand = method;
                        break;
                    }
                }
            } catch (Throwable ignored) {
                // Fabric, or an era whose Forge dropped the client command handler.
            }

            commandHandler = executeCommand == null ? null : found;
        }

        return commandHandler;
    }

}
