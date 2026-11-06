package io.github.adang101.alpineshred.compat.create;

/**
 * What common code may know about Create (phase 9 plan, decision 1): no Create type appears here, so the mod
 * loads without Create. Each loader implements it in its own {@code compat.create} package, and that class is
 * loaded only behind {@code PlatformHooks.isCreatePresent()}; the kinetic drive and wrench support grow this
 * interface in later work packages.
 */
public interface CreateBridge {
    /** True when Create's own registry content (its blocks) is present: proves Create loaded, not just that its jar is listed. */
    boolean live();

    /** The installed Create version string, for logs and the load test. */
    String version();
}
