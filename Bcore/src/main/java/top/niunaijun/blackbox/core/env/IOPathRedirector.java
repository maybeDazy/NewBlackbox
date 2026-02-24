package top.niunaijun.blackbox.core.env;

/**
 * Interface for IO path redirection.
 *
 * Implementations in the host app module provide custom
 * path redirection logic (UUID-based container isolation)
 * that the Bcore engine uses during virtual process execution.
 *
 * Injected via {@link top.niunaijun.blackbox.BlackBoxCore#setPathRedirector(IOPathRedirector)}
 * during engine initialization.
 */
public interface IOPathRedirector {

    /**
     * Redirect an original file path to a container-isolated path.
     *
     * @param originalPath The original path the virtual app is trying to access
     * @param userId       The virtual user ID of the container
     * @return The redirected path, or originalPath if no redirection is needed
     */
    String redirect(String originalPath, int userId);
}
