# Private Chests

Bukkit plugin to have private chests that can also act as shops.

Start by renaming any container to `@your-username`, now that's your private
container. Others can see what's inside, but can't interact with it otherwise.
They can't take anything out, and can't break it.

You can turn such a chest into a shop by placing "price list" items in the
chest, e.g. rename an item to be called `16 golden carrots : 5 diamonds` and
place it in the chest, together with a bunch of golden carrots.

Now if someone clicks on the carrots, assuming they have diamonds in their own
inventory, the carrots will immediately get swapped for the matching amount of
diamonds.

Hoppers can only take items out of a chest if the hopper is also named with the
same username. Hoppers can still put items in the chest.

You can put anything after `@your-username`, as long as it is followed first by
a space or a single quote `'`, e.g. `@sunnyplexus's banana shop`.

Known shortcoming: if you place a single private chest, then someone can put
another chest left of it to create a double chest, which will take on the name
of the left chest, thus bypassing the mechanism.

There will still be game mechanisms that allow you to break the chest, e.g. TNT,
pistons, so this isn't tamperproof yet.

Build, require Clojure CLI

```
clojure -T:build-plugin
```

You can find the resulting jar in `target`
