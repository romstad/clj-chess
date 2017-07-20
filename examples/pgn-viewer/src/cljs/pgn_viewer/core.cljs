(ns pgn-viewer.core
  (:require [clj-chess.board :as board]
            [clj-chess.core :as chess]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]))

(def debug?
  ^boolean goog.DEBUG)


;; App DB

(def default-db
  {:game (chess/new-game)})


;; Subscriptions

(re-frame/reg-sub
  :game
  (fn [db] (:game db)))


;; Events

(re-frame/reg-event-db
  :initialize-db
  (fn  [_ _] default-db))


(re-frame/reg-event-db
  :text-input
  (fn [db [_ pgn]]
    (assoc db :game
           (chess/to-beginning (chess/game-from-pgn pgn)))))


(re-frame/reg-event-db
  :back
  (fn [db _]
    (update db :game chess/step-back)))


(re-frame/reg-event-db
  :forward
  (fn [db _]
    (update db :game chess/step-forward)))



;; Views

(def img-url-prefix "http://res.cloudinary.com/ds1kquy7j/image/upload/")


(defn piece-img-url [piece]
  (str img-url-prefix
       (name piece)
       ".png"))


(defn square-to-coordinates [square]
  [(mod square 8) (- 7 (quot square 8))])


(defn square-view [[col row] piece sq-size]
  [:div {:style {:background-color (if (= (mod (+ col row) 2) 0)
                                     "rgb(200, 200, 200)"
                                     "rgb(140, 140, 140)")
                 :position :absolute
                 :top (* row sq-size)
                 :left (* col sq-size)
                 :width sq-size
                 :height sq-size}}
   (when-not (= piece :empty)
     [:div {:style {:position :absolute
                    :width sq-size
                    :height sq-size
                    :background-image (str "url(" (piece-img-url piece) ")")
                    :background-size (str sq-size "px " sq-size "px")}}])])


(defn board-view [board size]
  (into [:div {:style {:width size
                       :height size
                       :position :relative}}]
        (map (fn [s]
               [square-view
                (square-to-coordinates s)
                (chess/piece-on board s)
                (/ size 8)])
             (range 64))))


(defn board-or-error [game]
  (if game
    [board-view (chess/board game) 400.0]
    [:div "Invalid PGN"]))


(defn pgn-input-box []
  [:div
   [:textarea {:placeholder "Paste or type PGN game here."
               :rows 30
               :cols 80
               :on-change #(re-frame/dispatch [:text-input (.-value
                                                             (.-target %))])}]])


(defn root-view []
  (let [game (re-frame/subscribe [:game])]
    [:div
     [board-or-error @game]
     [:div
      [:button {:on-click #(re-frame/dispatch [:back])}
       "Back"]
      [:button {:on-click #(re-frame/dispatch [:forward])}
       "Forward"]]
     [pgn-input-box]]))


;; Init

(defn dev-setup []
  (when debug?
    (enable-console-print!)
    (println "dev mode")))


(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [root-view]
                  (.getElementById js/document "app")))


(defn ^:export init []
  (re-frame/dispatch-sync [:initialize-db])
  (dev-setup)
  (mount-root))
