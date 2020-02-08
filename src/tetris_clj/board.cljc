(ns tetris-clj.board
  (:require [ysera.error :refer [error]]
            [ysera.test :refer [is= is is-not]]
            [tetris-clj.utils :refer [pic->mat]]
            [tetris-clj.piece :refer [create-piece
                                      get-in-piece]]))

(defn create-board
  "Create an empty tetris game board. If a pic is provided, use it to build the game board instead."
  {:test (fn []
           (is= (create-board 3 3)
                {:matrix [[false false false]
                          [false false false]
                          [false false false]]
                 :width  3
                 :height 3})
           (is= (create-board [" # "
                               " ##"
                               "###"])
                ; Result is flipped b/c first inner vector is y=0
                {:matrix [[true true true]
                          [false true true]
                          [false true false]]
                 :width  3
                 :height 3}))}
  ([pic]
   (let [mat (pic->mat pic)]
     {:matrix mat
      :width  (-> mat
                  (first)
                  (count))
      :height (count mat)}))
  ([width height]
   {:matrix (-> (repeat height
                        (-> (repeat width false)
                            (vec)))
                (vec))
    :width  width
    :height height}))

(defn set-cell
  "Set the value of a cell in the board"
  {:test (fn []
           (is= (-> (create-board 2 2)
                    (set-cell 0 1 true)
                    :matrix)
                (pic->mat ["# "
                           "  "])))}
  [board x y val]
  (assoc-in board [:matrix y x] val))

(defn get-row
  "Get a row in the board"
  {:test (fn []
           (is= (-> (create-board ["###"
                                   "   "])
                    (get-row 0))
                (repeat 3 false))
           (is= (-> (create-board ["###"
                                   "   "])
                    (get-row 1))
                (repeat 3 true)))}
  [board y]
  (get-in board [:matrix y]))

(defn cell-full?
  "Check whether a cell (x,y) is occupied or not"
  {:test (fn []
           (is-not (-> (create-board ["   "])
                       (cell-full? 0 0)))
           (is (-> (create-board [" #"])
                   (cell-full? 1 0))))}
  [board x y]
  (get-in board [:matrix y x]))

(defn create-empty-row [width] (-> (repeat width false)
                                   (vec)))

(defn row-full?
  "Check whether a row in the board is full"
  {:test (fn []
           (let [board (create-board ["###"
                                      "   "])]
             (is-not (row-full? board 0))
             (is (row-full? board 1))))}
  [board y]
  (every? identity (get-row board y)))

(defn shift-down
  "Shift the rows above y=start down by step.  If step is not provided, it defaults to 1."
  {:test (fn []
           (is= (-> (create-board ["###"
                                   "   "
                                   "###"])
                    (shift-down 1)
                    :matrix)
                (pic->mat ["   "
                           "###"
                           "###"]))
           (is= (-> (create-board ["###"
                                   "   "
                                   "   "
                                   "   "])
                    (shift-down 0 3)
                    :matrix)
                (pic->mat ["   "
                           "   "
                           "   "
                           "###"])))}
  ([board start]
   (shift-down board start 1))
  ([board start step]
   {:pre [(< start (:height board))]}
   (reduce (fn [board y]
             (assoc-in board [:matrix y]
                       (or (get-row board (+ y step))
                           (create-empty-row (:width board)))))
           board
           (range start (:height board)))))

(defn clear-row
  "Clear a row in the board"
  {:test (fn []
           (is= (-> (create-board ["  #"
                                   "###"
                                   "# #"])
                    (clear-row 1)
                    :matrix)
                (pic->mat ["  #"
                           "   "
                           "# #"])))}
  [board y]
  (reduce (fn [board x]
            (set-cell board x y false))
          board
          (range (:width board))))

(defn clear-rows
  "Clear all filled rows where y is in [start, end) in the board and shift all rows above cleared rows down."
  {:test (fn []
           (is= (-> (create-board ["# #  "
                                   "#### "
                                   "#####"
                                   "#####"
                                   "#####"])
                    (clear-rows 0 3)
                    :matrix)
                (pic->mat ["     "
                           "     "
                           "     "
                           "# #  "
                           "#### "]))
           ; Handle split line-clears
           (is= (-> (create-board ["# #  "
                                   "#### "
                                   "#####"
                                   "## ##"
                                   "#####"])
                    (clear-rows 0 3)
                    :matrix)
                (pic->mat ["     "
                           "     "
                           "# #  "
                           "#### "
                           "## ##"])))}
  [board start end]
  (loop [b board
         y start]
    (if (>= y end)
      b
      (if (row-full? b y)
        (-> b
            (clear-row y)
            (shift-down y)
            (recur y))
        (recur b (inc y))))))

(defn add-piece
  "Add a piece on to board with lower-left corner at (x,y). Throw error if the piece overlaps with a full cell."
  ; TODO throw error
  {:test (fn []
           (is= (-> (create-board ["   "
                                   "   "
                                   "## "])
                    (add-piece (-> (create-piece "J" 3))

                               0 0)
                    :matrix)
                (pic->mat ["   "
                           "###"
                           "###"])))}
  [board piece x y]
  (reduce (fn [board [dx dy]]
            (set-cell board
                      (+ x dx)
                      (+ y dy)
                      true))
          board
          (get-in-piece piece :coords)))

(defn place-piece
  "Place a piece on the board, processing line clears"
  {:test (fn []
           ; Process a tetris (line clear), shift cells down
           (is= (-> (create-board ["    #"
                                   "    #"
                                   "# ###"])
                    (place-piece (create-piece "T")
                                 0 0)
                    :matrix)
                (pic->mat ["     "
                           "    #"
                           "### #"]))
           ; Process multiple simultaneous clears
           (is= (-> (create-board ["   #"
                                   "   #"
                                   "# ##"])
                    (place-piece (create-piece "T")
                                 0 0)
                    :matrix)
                (pic->mat ["    "
                           "    "
                           "   #"]))
           ; Process multiple simultaneous split clears
           (is= (-> (create-board ["###  "
                                   "# ## "
                                   "#### "])
                    (place-piece (create-piece "L" 2)
                                 3 0)
                    :matrix)
                (pic->mat ["     "
                           "     "
                           "# ###"]))
           ; No line clear
           (is= (-> (create-board ["    "
                                   "    "
                                   "#   "])
                    (place-piece (create-piece "T")
                                 0 0)
                    :matrix)
                (pic->mat ["    "
                           "### "
                           "##  "])))}
  [board piece x y]
  (-> board
      (add-piece piece x y)
      (clear-rows y (+ y (get-in-piece piece :height)))))