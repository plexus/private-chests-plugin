(ns lambdaisland.private-chests
  "Implementation of private chests with shop capabilities

  Start by naming a chest @username, only that user will be able to break the
  chest or interact with its inventory.

  You can add items in the chest that form a price list, e.g name the item \"16
  Golden Carrots : 3 Diamonds\" and put it in the chest. Now when someone clicks
  on a stack of golden carrots in the chest, it will exchage the golden carrots
  and diamonds between the two inventories.

  Current shortcomings:
  - Placing a chest left of a single chest will merge them and override the
    name, to be safe always put double chests
  - Make it possible to use the display name of renamed items in the price list
  "
  (:require [lambdaisland.witchcraft :as wc]
            [lambdaisland.witchcraft.events :as e]
            [clojure.string :as str]))

(defn as-material-name [s]
  (let [ms (str/replace (str/trim (str/lower-case s)) #"\s+" "-")
        m  (subs ms 0 (dec (count ms)))
        msk (keyword ms)
        mk (keyword m)]
    (cond
      (get wc/materials msk)
      msk
      (get wc/materials mk)
      mk)))

(defn price-list [inventory]
  (into {}
        (keep (fn [{:keys [display-name]}]
                (when display-name
                  (when-let [[_ a b c d] (re-find #"^(\d+)\s+([^:]+)?:\s*(\d+)\s+(.*)\s*$" display-name)]
                    [(as-material-name b)
                     [(/ (Long/parseLong c)
                         (Long/parseLong a))
                      (as-material-name d)]]))))
        (:contents (wc/inventory inventory))))

(defn other-player-inv? [inv player]
  (let [inv-name    (wc/display-name (wc/get-block inv))
        player-name (str "@" (wc/display-name player))]
    (and (= \@ (first inv-name))
         (not (or (= inv-name player-name)
                  (.startsWith inv-name (str player-name " "))
                  (.startsWith inv-name (str player-name "'")))))))

(defn install-handlers []
  (e/listen-raw!
   :block-break
   ::disallow-break
   (fn [e]
     (let [player-name (wc/display-name (wc/player e))
           block-name (wc/display-name (wc/get-block e))]
       (when (and block-name
                  (= \@ (first block-name))
                  (not (.startsWith block-name (str "@" player-name))))
         (e/cancel! e)))))

  (e/listen!
   :inventory-click
   ::handle-shopping
   (fn [x]
     (let [inv             (:clickedInventory x)
           inv-inv         (wc/inventory inv)
           player          (:whoClicked x)
           player-inv      (wc/inventory (:whoClicked x))
           price-list      (price-list inv)
           ^org.bukkit.inventory.ItemStack clicked-item (:currentItem x)
           clicked-amount  (.getAmount clicked-item)
           clicked-in-top? (= (:inventory x) (:clickedInventory x))]
       (when (other-player-inv? (:inventory x) player)
         (cond
           ;; This could be fine tuned, only allow double click if clicking in the
           ;; bottom and no matching items in the top, but just canceling it for
           ;; now.
           (= (:click x) org.bukkit.event.inventory.ClickType/DOUBLE_CLICK)
           (e/cancel! x)

           clicked-in-top?
           (do
             (e/cancel! x)
             (when (= (wc/material-name (:cursor x)) :air)
               (when-let [[coin-amount coin-type] (get price-list (wc/material-name (:currentItem x)))]
                 (let [total-price   (Math/floor (* clicked-amount coin-amount))
                       not-removed   (wc/remove-inventory player coin-type total-price)
                       removed-count (- total-price (transduce (map #(.getAmount ^org.bukkit.inventory.ItemStack %)) + 0 (vals not-removed)))
                       paid-for      (Math/floor (/ removed-count coin-amount))]
                   (wc/remove-inventory inv (wc/material clicked-item) paid-for)
                   (wc/add-inventory player (wc/material clicked-item) paid-for)
                   (wc/add-inventory inv coin-type removed-count))))))))))

  (e/listen!
   :inventory-drag
   ::disallow-dragging
   (fn [x]
     (let [^org.bukkit.inventory.Inventory inv (:inventory x)
           player (:whoClicked x)]
       (when (and (other-player-inv? inv player)
                  (some #(< % (.getSize inv)) (keys (:newItems x))))
         (e/cancel! x)))))

  (e/listen-raw!
   :inventory-move-item
   ::disallow-hopper-draining
   ;; This one we need to be careful not to waste cycles, do a quick
   ;; short-circuit unless the source has an owner
   (fn [^org.bukkit.event.inventory.InventoryMoveItemEvent e]
     (when-let [source (some-> e .getSource wc/get-block wc/display-name)]
       (when (= \@ (first source))
         (when-let [destination (some-> e .getDestination wc/get-block wc/display-name)]
           (when (or (not= \@ (first destination))
                     (not= (re-find #"@\w+" source)
                           (re-find #"@\w+" destination)))
             (e/cancel! e)))))))

  (e/listen-raw!
   :inventory-click
   ::prevent-illegal-rename
   (fn [^org.bukkit.event.inventory.InventoryClickEvent e]
     (when (and (= org.bukkit.event.inventory.InventoryType/ANVIL
                   (some-> e .getInventory .getType))
                (= org.bukkit.event.inventory.InventoryType$SlotType/RESULT
                   (.getSlotType e)))
       (when-let [item-name (some-> e .getCurrentItem wc/display-name)]
         (when (and (= \@ (first item-name))
                    (not (.startsWith item-name (str "@" (wc/display-name (.getWhoClicked e))))))
           (e/cancel! e)))))))

(defn remove-handlers []
  (e/unlisten! :block-break ::disallow-break)
  (e/unlisten! :inventory-click ::handle-shopping)
  (e/unlisten! :inventory-drag ::disallow-dragging)
  (e/unlisten! :inventory-move-item ::disallow-hopper-draining)
  (e/unlisten! :inventory-click ::prevent-illegal-rename))
