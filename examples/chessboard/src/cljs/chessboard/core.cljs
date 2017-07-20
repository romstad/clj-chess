(ns chessboard.core
  (:require [clj-chess.board :as board]
            [clj-chess.core :as chess]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]))


;; App DB

(def default-db
  {:game (chess/new-game)
   :board-rotated? false
   :selected-square nil
   :candidate-moves []})


;; Subscriptions

(re-frame/reg-sub
  :board
  (fn [db] (chess/board (:game db))))


(re-frame/reg-sub
  :board-rotated?
  (fn [db] (:board-rotated? db)))


;; Events

(re-frame/reg-event-db
  :initialize-db
  (fn [_ _] default-db))


(re-frame/reg-event-db
  :rotate-board
  (fn [db _]
    (update db :board-rotated? not)))


(re-frame/reg-event-db
  :back
  (fn [db _]
    (update db :game chess/step-back)))


(re-frame/reg-event-db
  :forward
  (fn [db _]
    (update db :game chess/step-forward)))


(re-frame/reg-event-db
  :square-pressed
  (fn [db [_ square]]
    (let [moves (filter (fn [m]
                          (= (chess/move-to m) square))
                        (:candidate-moves db))]
      (if-let [move (first moves)]
        (-> db
            (assoc :selected-square nil :candidate-moves [])
            (update :game #(chess/add-uci-move % (chess/move-to-uci move))))
        (let [board (chess/board (:game db))
              new-moves (filter (fn [m]
                                  (= (chess/move-from m) square))
                                (chess/legal-moves board))]
          (assoc db
                 :candidate-moves new-moves
                 :selected-square (if (empty? new-moves)
                                    nil
                                    square)))))))


;; Views

(def img-url-prefix "http://res.cloudinary.com/ds1kquy7j/image/upload/")


(defn piece-img-url [piece]
  (str img-url-prefix
       (name piece)
       ".png"))


(defn square-to-coordinates [square rotated?]
  (if rotated?
    [(- 7 (mod square 8)) (quot square 8)]
    [(mod square 8) (- 7 (quot square 8))]))


(defn square-view [[col row] piece sq-size on-click]
  [:div {:style {:background-color (if (= (mod (+ col row) 2) 0)
                                     "rgb(200, 200, 200)"
                                     "rgb(140, 140, 140)")
                 :position :absolute
                 :top (* row sq-size)
                 :left (* col sq-size)
                 :width sq-size
                 :height sq-size}
         :on-click on-click}
   (when-not (= piece :empty)
     [:div {:style {:position :absolute
                    :width sq-size
                    :height sq-size
                    :background-image (str "url(" (piece-img-url piece) ")")
                    :background-size (str sq-size "px " sq-size "px")}}])])


(defn board-view [board size rotated?]
  (into [:div {:style {:width size
                       :height size
                       :position :relative}}]
        (map (fn [s]
               [square-view
                (square-to-coordinates s rotated?)
                (chess/piece-on board s)
                (/ size 8)
                #(re-frame/dispatch [:square-pressed s])])
             (range 64))))


(defn root-view []
  (let [board @(re-frame/subscribe [:board])
        rotated? @(re-frame/subscribe [:board-rotated?])]
    [:div
     [board-view board 400.0 rotated?]
     [:div
      [:button {:on-click #(re-frame/dispatch [:rotate-board])}
       "Rotate"]
      [:button {:on-click #(re-frame/dispatch [:back])}
       "Back"]
      [:button {:on-click #(re-frame/dispatch [:forward])}
       "Forward"]]]))


;; Init

(def debug?
   ^boolean goog.DEBUG)

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
