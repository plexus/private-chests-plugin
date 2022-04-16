package com.lambdaisland;

import clojure.lang.RT;
import clojure.lang.Var;
import clojure.lang.Symbol;
import clojure.lang.DynamicClassLoader;
import org.bukkit.plugin.java.JavaPlugin;

public class PrivateChestsPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        ClassLoader pluginLoader = this.getClass().getClassLoader();
        Thread.currentThread().setContextClassLoader(new DynamicClassLoader(pluginLoader));

        Var require = RT.var("clojure.core", "require");

        require.invoke(Symbol.intern("lambdaisland.witchcraft"));
        RT.var("lambdaisland.witchcraft", "init-xmaterial!").invoke();

        require.invoke(Symbol.intern("lambdaisland.private-chests"));
        RT.var("lambdaisland.private-chests", "install-handlers").invoke();
    }

    @Override
    public void onDisable() {
        RT.var("lambdaisland.private-chests", "remove-handlers").invoke();
    }
}
