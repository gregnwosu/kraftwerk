(ns kraftwerk.die-mensch-machine.space-lab
  (:use
    [leipzig.melody]
    [leipzig.live]
    [leipzig.chord]
    [leipzig.canon]
    [leipzig.scale])
  (:require
    [overtone.live :as overtone]))

; Utilities
(defn mapthen [f & args]
  (->> args
       (apply map f)
       (reduce #(then %2 %1))))

(def inv inversion)

; Notes
(def beat
  (->>
    (rhythm (repeat 32 1/4))
    (where :drum (is :tick))
    (with (->> (rhythm (repeat 8 1)) (having :drum (cycle [:kick :tock]))))
    (where :part (is :beat))))

(def intro
  (let [line (->> (range 2 (+ 2 16))
                  (phrase (repeat 1))
                  (where :duration (partial + 3))
                  (where :part (is :echo)))]
    (->> line
         (canon (simple 1/2))
         (canon (simple 12))
         (where :duration (is 4)))))

(def chorus
  (let [chords (->> [nil (-> triad (root 2) (inv 1))
                     nil (-> triad (root -4) (update-in [:iii] #(+ % 1/2)))]
                    (phrase (cycle [1 3]))
                    (where :part (is :chords)))
        melodya  (->> [0 2 3 0] (phrase [1/2 7/2 1/2 8/2])
                      (after -1/2)
                      (where :part  (is :melody)))
        melodyb  (->> melodya drop-last (then (phrase [8/2] [7]))
                      (where :part (is :melody)))
        harmony  (->> [7] (phrase [8]) (where :part (is :harmony)))
        echo  (->> (concat (repeat 16 2) (repeat 16 0)) (phrase (repeat 1/4))
                   (where :part (is :echo)))
        accompaniment (reduce with [echo chords harmony beat])] 
    (->> accompaniment
         (then (with accompaniment melodya))
         (then accompaniment)
         (then (with accompaniment melodyb)))))
         
(def verse
  (let [bass (->> [0 2 3 6 7]
                  (phrase [7.5 0.5 3.5 0.5 4])
                  (where :pitch lower)
                  (where :part (is :bass)))
        melodya (->> [5 6 7 6 5 4 5 6]
                     (phrase [1 1/2 3/2 1/2 1/2 1 1/2 5/2])
                     (canon (comp (interval -2) (simple 8)))
                     (where :part (is :melody)))
        melodyb (->> [5 6 7 8 9]
                     (phrase [1 1/2 3/2 1/2 1/2])
                     (then (->> melodya (drop 5) (drop-last 3) (after -4)))
                     (then (phrase [1 1/2 5/2] [2 -1 0]))
                     (where :part (is :melody)))
        harmony (->> [7 6 5 4] (phrase (repeat 4)) (where :part (is :harmony)))
        echo (->> (concat (repeat 32 0) (repeat 14 3) (repeat 2 6) (repeat 16 0))
                  (phrase (repeat 1/4))
                  (where :part (is :echo)))
        accompaniment (reduce with [bass harmony echo (times 2 beat)])]
    (->> (with melodya accompaniment)
         (then (with melodyb accompaniment)))))

(def track
  (->>
    intro
    (then (->> chorus (filter #(-> % :part #{:chords :melody}))))
    (then (after 4 chorus))
    (then verse)
    (wherever :pitch, :pitch (comp C minor)) 
    (where :duration (bpm 105)) 
    (where :time (bpm 105))))

; Instruments
(overtone/definst hat  [] 
  (-> (overtone/perc 0 0.05)
      (overtone/env-gen :action overtone/FREE)
      (* (overtone/white-noise)))) 

(overtone/definst snare []
  (-> (overtone/perc 0 0.05)
      (overtone/env-gen :action overtone/FREE)
      (* (overtone/mix [(overtone/sin-osc 200) (overtone/white-noise)]))))

(overtone/definst bass-drum []
  (-> (overtone/perc 0 0.05)
      (overtone/env-gen :action overtone/FREE)
      (* 3 (overtone/sin-osc 40))))

(overtone/definst bass [freq 440 dur 1000]
  (let [envelope (overtone/env-gen (overtone/asr 0 1 1)
                                   (overtone/line:kr 1.0 0.0 (/ dur 1000))
                                   :action overtone/FREE)
        level (overtone/env-gen (overtone/perc 0 3)
                                :level-scale 6000)
        osc (overtone/mix  [(overtone/saw freq)
                            (overtone/saw (* freq 1.005))
                            (overtone/pulse (/ freq 2) 0.5)])]
    (-> osc (overtone/lpf (+ 100 level)) (* envelope))))

(overtone/definst solo [freq 440 dur 1000]
  (let [envelope (overtone/env-gen (overtone/asr 0.2 1 2)
                                   (overtone/line:kr 1.0 0.0 (/ dur 1000))
                                   :action overtone/FREE)
        level  (overtone/env-gen (overtone/adsr 0.4 1.2 0.7 2)
                                 :level-scale 800)
        osc  (overtone/mix
               [(overtone/saw (overtone/lag freq 0.1))
                (overtone/saw (overtone/lag (* freq 1.005) 0.1))])]
    (-> osc (* 2) (overtone/lpf (+ 600 level)) (* envelope))))

(overtone/definst string [freq 440 dur 1000]
  (let [envelope (overtone/env-gen (overtone/asr 0.2 1 0.5)
                                   (overtone/line:kr 1.0 0.0 (/ dur 1000))
                                   :action overtone/FREE)
        osc (overtone/saw freq)]
    (-> osc (overtone/lpf 1500) (* envelope) (* 1/2))))

(overtone/definst res [freq 440 dur 350]
  (-> (overtone/perc 0 (* 2 (/ dur 1000)))
      (overtone/env-gen :action overtone/FREE)
      (* (overtone/mix [(overtone/saw freq) (overtone/pulse (/ freq 2) 0.5)]))
      (overtone/rlpf 800 0.1)
      (* 1/2)))

(overtone/definst poly [freq 440 dur 1000]
  (let [envelope (overtone/env-gen (overtone/asr 0.2 1 0.1)
                                   (overtone/line:kr 1.0 0.0 (/ dur 1000))
                                   :action overtone/FREE)
        level (overtone/env-gen (overtone/asr 7 1 0.2)
                                :level-scale 12000)
        osc1 (overtone/mix
               (map
                 #(overtone/pulse (* freq %) (+ 0.5 (* 0.3 (overtone/lf-cub 2))))
                 [1 1.007]))
        osc2 (overtone/pulse (/ freq 2) 0.3)]
    (* 1/3 envelope (overtone/rlpf (+ osc1 osc2) level 0.9))))

; Arrangement
(def kit {:kick bass-drum
          :tick hat
          :tock snare})

(defmethod play-note :beat [{drum :drum}]
  ((drum kit)))

(defmethod play-note :bass [{midi :pitch ms :duration}]
  (-> midi overtone/midi->hz (bass ms)))

(defmethod play-note :melody [{midi :pitch ms :duration}]
  (-> midi overtone/midi->hz (solo ms)))

(defmethod play-note :harmony [{midi :pitch ms :duration}]
  (-> midi overtone/midi->hz (* 2) (string ms))) 

(defmethod play-note :echo [{midi :pitch ms :duration}]
  (-> midi overtone/midi->hz (res ms)))

(defmethod play-note :chords [{midi :pitch ms :duration}]
  (-> midi overtone/midi->hz (poly ms)))

(defmethod play-note :default [note]
  (print (str "No part for " note ". ")))

