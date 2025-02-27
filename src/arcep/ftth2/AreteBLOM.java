/*
 * Copyright (c) 2025, Autorité de régulation des communications électroniques, des postes et de la distribution de la presse
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package arcep.ftth2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.collect.HashBasedTable;

@SuppressWarnings("serial")
public class AreteBLOM extends Arete {
    
    public Noeud n; // extrémité avale en étiquette
    private final List<AreteBLOM> filles = new ArrayList<>(); // arêtes filles pour la structure en arbre
    public int modePoseSortie; // 0 = aérien (d'origine), 1 = reconstruit aérien, 2 = souterrain réutilisable (d'origine), 3 = reconstruit souterrain
    
    //Compte des lignes avant mutualisation
    private int lignesZMD = 0;
    private int lignesZTD_BD = 0;
    private int lignesZTD_HD_PMint = 0;
    private int lignesZTD_HD_PMext = 0;
    
    //Compte des lignes après mutualisation (et hors surcapacité)
    private final Map<String, Integer> demandeFibresMutu = new HashMap<>();
    private final Map<String, Integer> demandeFibresNonMutu = new HashMap<>();
    private final Map<String, Integer> nbLiensCollectePM = new HashMap<>();
    
    private final Map<String,Integer> idPM = new HashMap<>();
    
    // Booléen indiquant si l'arête porte des fibres de transport, utilisée pour attribuer le modePoseSortie
    private boolean hasNonMutu = false;
    
    // UO
    private HashBasedTable<Boolean, String, int[]> nbCables; // on utilise true et false pour mutu et non mutu, et la string pour les zones
    private HashBasedTable<Boolean,String,Double> sections; // on utilise true et false pour mutu et non mutu, et la string pour les zones
    private double sectionTotale;
    private HashBasedTable<Boolean, String, int[]> boitiersEpissurage; // on utilise true et false pour mutu et non mutu, et la string pour les zones;
    private HashBasedTable<Boolean, String, Integer> epissures; // on utilise true et false pour mutu et non mutu, et la string pour les zones
    
    // pour pouvoir tracer des shapefiles
    private final Coordinate[] points;
    
    // champs techniques
    private HashBasedTable<Boolean, String, Double> distancesDernierBoitier; // true/false pour mutu/nonMutu, String pour zone, champ technique pour la pose des boitiers
    private int niveau = 0; // champ technique pour le "coloriage"
    
    // constructeur utilisé pour créer la root
    public AreteBLOM(Noeud n){
        this.id = 999999;
        longueur = 0;
        this.n = n;
        this.points = new Coordinate[0];
    }
    
    // constructeur utilisé lors de la construction de l'arbre à partir des fichiers intermédiaires (et donc d'objets Arete) dans Deploiement
    public AreteBLOM(Arete a, Noeud n, Noeud amont){
        this.id = a.id;
        this.modePose = a.modePose;
        this.longueur = a.longueur;
        this.n = n;
        this.lignesZMD = 0;
        this.lignesZTD_BD = 0;
        this.lignesZTD_HD_PMext = 0;
        this.lignesZTD_HD_PMint = 0;
        this.points = a.getPoints(amont,n);
    }
    
     // constructeur utilisé lors de la fusion des arêtes pour recopier en ajoutant la longueur
    public AreteBLOM(AreteBLOM a, double longueur, List<Coordinate> listePoints){
        this.id = a.id;
        this.n = a.n;
        this.modePose = a.modePose;
        this.longueur = longueur + a.longueur;
        this.lignesZMD = a.lignesZMD;
        this.lignesZTD_BD = a.lignesZTD_BD;
        this.lignesZTD_HD_PMext = a.lignesZTD_HD_PMext;
        this.lignesZTD_HD_PMint = a.lignesZTD_HD_PMint;
        this.points = listePoints.toArray(new Coordinate[listePoints.size()+a.points.length]);
        for (int i = 0;i<a.points.length;i++){
            this.points[listePoints.size()+i] = a.points[i];
        }
    }
    
    /////////////////////
    /// méthodes utilisées lors de la construction et simplification de l'arbre d'arête BLOM dont la racine est le champ root de BLO
    /////////////////////
    
    // ajout d'une arête fille
    public void addFille(AreteBLOM a){
        filles.add(a);
    }
    
    public List<AreteBLOM> getFilles(){
        return filles;
    }
    
    public void addNoeudLocal(Noeud n, Set<String> zones){
        for (String zone : zones){
            this.ajoutLignes(n.demandeLocaleExt(zone), zone);
            if (zone.equals("ZTD_HD")) this.ajoutLignes(n.demandeLocaleInt(), "ZTD_HD_PMint");
        }
    }

    private void ajoutLignes(int lignes, String zone){
        switch(zone){
            case "ZMD":
                this.lignesZMD += lignes;
                break;
            case "ZTD_BD":
                this.lignesZTD_BD += lignes;
                break;
            case "ZTD_HD_PMint":
                this.lignesZTD_HD_PMint += lignes;
                break;
            case "ZTD_HD":
                this.lignesZTD_HD_PMext += lignes;
                break;
        }
    }
        
    public void calculDemandeRoot(){
        for (AreteBLOM a : filles){
            this.lignesZMD += a.lignesZMD;
            this.lignesZTD_BD += a.lignesZTD_BD;
            this.lignesZTD_HD_PMext += a.lignesZTD_HD_PMext;
            this.lignesZTD_HD_PMint += a.lignesZTD_HD_PMint;
        }
    }
    
    public int totalLignes() {
        return lignesZMD + lignesZTD_BD + lignesZTD_HD_PMext + lignesZTD_HD_PMint;
    }
    
    // fonction récursive terminale pour la création d'un arbre simplifié à partir de l'existant
    public void fusionAretes(AreteBLOM mere, double longueur, List<Coordinate> listePoints){
        if ((filles.size() == 1) && (n.demandeLocaleTotale() == 0) && (filles.get(0).modePose == modePose)){ // le cas où on continue sans rien ajouter au graphe : une seule arête fille et pas de lignes au noeud
            for (int i = 0;i<this.points.length-1;i++){
                listePoints.add(this.points[i]);
            }
            filles.get(0).fusionAretes(mere, longueur+this.longueur, listePoints);
        }
        else{
            AreteBLOM nouvelleArete = new AreteBLOM(this, longueur, listePoints);
            mere.addFille(nouvelleArete);            
            // itération en profondeur
            for (AreteBLOM fille : filles){
                fille.fusionAretes(nouvelleArete,0, new ArrayList<>());
            }
        }
    }
    
    //////////////////////////
    /// méthodes utilisées pour le placement des PM
    //////////////////////////
    
    // fonction récursive qui place les PM et renvoie le nb de PM actuellement existants dans son sous-arbre
    public int posePMExtMediane(String zone, double seuilDistance, int seuilLignes){
        
        // étape 1 : appels récursifs sur les arêtes filles et identification de celles qui ont des PM a priori "regroupables" (nbLignes < 300)
        List<AreteBLOM> regroupables = new ArrayList<>();
        int nbPM = 0;
        for (AreteBLOM fille : this.filles){
            nbPM += fille.posePMExtMediane(zone, seuilDistance, seuilLignes);
            if (fille.n.taillePM(zone) < seuilLignes)
                regroupables.add(fille);
        }
        
        // étape 2 : on caclcule la différence entre le nb de lignes dont la longueur PM-PBO serait au-dessus ou au dessous du seuil si on posait le PM là où on est
        int delta = -this.n.demandeLocaleExt(zone);
        for (AreteBLOM fille : regroupables){
            double seuil = seuilDistance - fille.longueur;
            fille.n.lignesInfSeuil = 0;
            fille.n.lignesSupSeuil = 0;
            for (Double d : fille.n.distri){
                if (d > seuil)
                    fille.n.lignesSupSeuil++;
                else
                    fille.n.lignesInfSeuil++;
            }
            delta = delta + fille.n.lignesSupSeuil - fille.n.lignesInfSeuil;
        }
        
        // étape 3 : tant qu'on est dans le mauvais cas on sort des regroupables les PM qui font le plus pencher la balance du mauvais côté
        while(delta > 0){ // pas possible si plus que de la demande locale (même = 0) --> termine bien
            AreteBLOM filleHausseMax = this; // de telle sorte qu'on puisse tester sans échec le dernier terme de la condition ci-dessous (taillePM renvoie 0 à ce stade)
            int hausseMax = 0, hausse;
            for (AreteBLOM fille : regroupables){
                hausse = fille.n.lignesSupSeuil - fille.n.lignesInfSeuil;
                if (hausse > hausseMax || (hausse == hausseMax && fille.n.taillePM(zone) > filleHausseMax.n.taillePM(zone))){
                    hausseMax = hausse;
                    filleHausseMax = fille;
                }
            }
            regroupables.remove(filleHausseMax);
            delta = delta - filleHausseMax.n.lignesSupSeuil + filleHausseMax.n.lignesInfSeuil;
        }
        
        // étape 4 : on peut désormais placer un PM respectant le critère de distance et "déclasser" ceux qui sont regroupés
        int demandePM = n.demandeLocaleExt(zone);
        this.n.distri = new ArrayList<>();
        for (int i = 0;i<demandePM;i++){
            this.n.distri.add(0.0);
        }
        for (AreteBLOM fille : regroupables){
            demandePM += fille.n.taillePM(zone);
            fille.n.setTaillePM(zone,0);
            ListIterator<Double> iterator = this.n.distri.listIterator();
            double longueurFille = fille.longueur;
            for (Double d : fille.n.distri){
                d += longueurFille;
                while (iterator.hasNext() && iterator.next()<d){}
                if (iterator.hasPrevious() && iterator.hasNext()) iterator.previous();
                iterator.add(d);
            }
            fille.n.distri = null;
        }
        this.n.setTaillePM(zone, demandePM);
        
        // étape 5 : on renvoie le bon nombre de PM
        return nbPM - regroupables.size() + 1;
    }
    
        
    public int posePMExtMoyenne(String zone, double seuilDistance, int seuilLignes){
        
        // étape 1 : appels récursifs sur les arêtes filles et identification de celles qui ont des PM a priori "regroupables" (nbLignes < 300)
        List<AreteBLOM> regroupables = new ArrayList<>();
        int nbPM = 0;
        for (AreteBLOM fille : this.filles){
            nbPM += fille.posePMExtMoyenne(zone, seuilDistance, seuilLignes);
            if (0 < fille.n.taillePM(zone) && fille.n.taillePM(zone)< seuilLignes)
                regroupables.add(fille);
        }
        
        // étape 2 : on calcule la distance cumulée PM-PBO  si on posait le PM là où on est en regroupant tous les fils a priori regroupables
        double deltaDistanceCumulee = 0;
        int nbLignesPM = this.n.demandeLocaleExt(zone);
        for (AreteBLOM fille : regroupables){
            deltaDistanceCumulee += (fille.longueur + fille.n.longueurMoyenne)*fille.n.taillePM(zone);
            nbLignesPM += fille.n.taillePM(zone);
        }
        deltaDistanceCumulee -= nbLignesPM*seuilDistance;
        
        // étape 3 : tant qu'on est dans le mauvais cas on exclut des regroupables les PM qui font le plus pencher la balance du mauvais côté
        while(Math.floor(deltaDistanceCumulee)  > 0){ // pas possible si plus que de la demande locale (car alors delta <= 0) --> termine bien //le floor sert à éviter le cas des négatifs si proche de 0 que le test est positif
            
            AreteBLOM filleDeltaMax = this; // de telle sorte qu'on puisse tester sans échec le dernier terme de la condition ci-dessous (taillePM renvoie 0 à ce stade)
            double deltaMax = 0, delta;
            for (AreteBLOM fille : regroupables){
                delta = (fille.longueur + fille.n.longueurMoyenne - seuilDistance)*fille.n.taillePM(zone);
                if (delta > deltaMax || (delta == deltaMax && fille.n.taillePM(zone) > filleDeltaMax.n.taillePM(zone))){
                    deltaMax = delta;
                    filleDeltaMax = fille;
                }
            }
            regroupables.remove(filleDeltaMax);
            deltaDistanceCumulee -= deltaMax;
            nbLignesPM -= filleDeltaMax.n.taillePM(zone);
        }
        
        // étape 4 : on peut désormais placer un PM respectant le critère de distance et "déclasser" ceux qui sont regroupés
        if (nbLignesPM > 0){
            this.n.setTaillePM(zone, nbLignesPM);
            this.n.longueurMoyenne = deltaDistanceCumulee / (double) nbLignesPM + seuilDistance;
            nbPM++;
        }
        for (AreteBLOM fille : regroupables){
            fille.n.setTaillePM(zone,0);
        }
        
        // étape 5 : on renvoie le bon nombre de PM
        return nbPM - regroupables.size();
    }

    public void listingPMint(List<Noeud> listePMint, double distance){
        double d = distance+this.longueur;
        this.n.distanceAuCentre = d;
        if (this.n.isPMint()){
            listePMint.add(n);
        }
        for (AreteBLOM fille : this.filles){
            fille.listingPMint(listePMint, d);
        }
    }
    
    public int numerotePM(String zone, int prochainNumero){
        int numeroSuivant = prochainNumero;
        if (this.n.isPMExt(zone)){
            this.n.numPM = numeroSuivant;
            numeroSuivant++;
        }
        for (AreteBLOM a : this.filles){
            numeroSuivant = a.numerotePM(zone, numeroSuivant);
        }
        return numeroSuivant;
    }
    
    /**
     * fonction récursive permettant d'établir la profondeur maximale de l'arbre
     * à partir d'une arête donnée. Utilisée par BLO sur root.calculNiveau() pour
     * connaitre le nombre maximal d'arêtes parcourues lorsque l'on descend l'arbre
     * depuis le NRO (coloriage des feuilles (niveau = 1) vers le tronc)
     * 
     * @return (int) nombre maximal d'arêtes dans une lignée issue de l'AreteBLOM
     */
    public int calculNiveau(){
        int niv = 0;
        for (AreteBLOM a : this.filles){
            int nivA = a.calculNiveau();
            if (nivA > niv)
                niv = nivA;
        }
        this.niveau = niv + 1;
        return this.niveau;
    }
    
    /**
     * Fonction récursive permettant d'établir la longueur totale de GC dans
     * l'arbre d'AreteBLOM issues d'un NRO
     * 
     * @return (double) longueur totale de GC utilisée par le FttH dans la ZANRO
     */
    public double longueurTotale(){
        double longueurTot = this.longueur;
        for (AreteBLOM a : this.filles){
            longueurTot+=a.longueurTotale();
        }
        return longueurTot;
    }
    
    /**
     * Fonction récursive permettant d'établir la longueur totale de GC reconstruit
     * dans l'arbre d'AreteBLOM issues d'un NRO
     * 
     * @return (double) longueur totale de GC reconstruit utilisée par le FttH
     * dans la ZANRO
     */
    public double longueurTotaleReconstruite(){
        double longueurTotRec = 0;
        if (this.modePose == 4 || this.modePose > 10) longueurTotRec = this.longueur;
        for (AreteBLOM a : this.filles){
            longueurTotRec+=a.longueurTotaleReconstruite();
        }
        return longueurTotRec;
    }
    
    ////////////////////////////////////////////
    /// méthodes pour établir le modePoseSortie
    ///////////////////////////////////////////
    
    // fonction récursive pour établire le modePose en sortie en cas de coloriage
    public double setModesPose(int niveau, double seuilAerien, double seuilPleineTerreAerien, double seuilPleineTerreSouterrain, double longueurParcourue){
        double longueurP = longueurParcourue;
        if (niveau == this.niveau){
            if(longueurParcourue < seuilAerien){
                this.modePoseSortie = 0; 
            } else if(longueurParcourue < seuilPleineTerreAerien){
                this.modePoseSortie = 1;
            } else if(longueurParcourue < seuilPleineTerreSouterrain){
                this.modePoseSortie = 3;
            }else{
                this.modePoseSortie = 2; 
            }
            longueurP += this.longueur;
        } else if (niveau < this.niveau){
            Queue<AreteBLOM> file = new ArrayBlockingQueue<>(filles.size(), true, filles);
            while(!file.isEmpty()){
                longueurP = file.remove().setModesPose(niveau, seuilAerien, seuilPleineTerreAerien, seuilPleineTerreSouterrain, longueurP);
            }
        }
        return longueurP;
    }
    
    // fonction pour établir le modePose en sortie à partir de celui d'entrée (pas de coloriage)
    public void setModesPose(){
        
        if (this.modePose <= 2) {
            // mode de pose initial = 0, 1 ou 2 correspondant à du GC aérien ou en façade
            this.modePoseSortie = 0;    // GC aérien existant
        } else if (this.modePose == 3 || (this.modePose >= 5 && this.modePose <= 10)) {
            // modes de pose 3 = "immeuble" et 5-10 correspondant à du GC souterrain
            this.modePoseSortie = 2;    // GC souterrain existant
        } else {
            // par défaut : reconstruction en souterrain s'il y a du transport, en aérien sinon
            if (this.hasNonMutu) {
                this.modePoseSortie = 3;    // GC reconstruit en souterrain
            } else {
                this.modePoseSortie = 1;    // GC reconstruit en aérien
            }
        }
        
        // Récursion
        for (AreteBLOM a : this.filles){
            a.setModesPose();
        }
    }
    
    /**
     *  Fonction récursive permettant l'attribution de mode de pose de sortie pour le GC,
     *  d'après le mode de pose d'entrée lorsqu'il est utilisable directement, et d'après
     *  les options choisies par l'utilisateur (distinction entre transport et distribution)
     *  lorsqu'il est reconstruit
     * 
     * @param recTaer (boolean) le GC à reconstruire l'est en aérien s'il porte 
     *          des fibres de transport. Si faux, il est reconstruit en souterrain.
     * @param forceTsout (boolean) le GC portant du transport est reconstruit en souterrain
     *          lorsqu'il est à reconstruire ou bien lorsqu'il est existant mais aérien.
     *          Prend le pas sur recTaer (si forceTsout est à vrai, les arêtes de GC à
     *          reconstruire le seront en souterrain même si recTaer est vrai.
     * @param recDaer (boolean) le GC ne portant pas de transport (distribution uniquement)
     *          est reconstruit en aérien. Si faux, il est reconstruit en souterrain.
     */
    public void setModesPose(boolean recTaer, boolean forceTsout, boolean recDaer){
        
        if (this.hasNonMutu){
            if (this.modePose <= 2) {
                if (forceTsout) this.modePoseSortie = 3;    // reconstruction en souterrain
                else this.modePoseSortie = 0;               // aérien existant
            } else if (this.modePose == 3 || (this.modePose >= 5 && this.modePose <= 10)) {
                this.modePoseSortie = 2;                    // GC souterrain existant
            } else {
                if (recTaer && !forceTsout) this.modePoseSortie = 1;       // reconstruction en aérien
                else this.modePoseSortie = 3;               // reconstruction en souterrain
            }
        } else {
            if (this.modePose <= 2) {
                this.modePoseSortie = 0;                    // GC aérien existant
            } else if (this.modePose == 3 || (this.modePose >= 5 && this.modePose <= 10)) {
                this.modePoseSortie = 2;                    // GC souterrain existant
            } else {
                if (recDaer) this.modePoseSortie = 1;       // reconstruction en aérien
                else this.modePoseSortie = 3;               // reconstruction en souterrain
            }
        }
        
        // Récursion
        for (AreteBLOM a : this.filles){
            a.setModesPose(recTaer, forceTsout, recDaer);
        }
    }
    
    /**
     * Fonction récursive permettant l'attribution de mode de pose de sortie pour le GC,
     *  - d'après le mode de pose d'entrée lorsqu'il est utilisable directement,
     *  - et par coloriage lorsqu'il est reconstruit
     * 
     * @param niveau (int) indique le niveau de profondeur dans l'arbre à laquelle s'applique la méthode
     * @param seuilReconstructionAerienSouterrain (double) longueur à reconstruire en aérien
     * @param longueurReconstruiteParcourue (double) longueur de GC reconstruit à laquelle un mode pose a déjà été attribué
     * @return (double) longueur reconstruite parcourue dans des niveaux inférieurs (plus vers les feuilles)
     */
    public double setModesPose(int niveau, double seuilReconstructionAerienSouterrain, double longueurReconstruiteParcourue){
        double longueurRecP = longueurReconstruiteParcourue;
        if (niveau == this.niveau){
            switch(this.modePose){
                case 0:
                case 1:
                case 2:
                    // aérien existant
                    this.modePoseSortie = 0;
                    break;
                case 3:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                case 10:
                    // souterrain existant
                    this.modePoseSortie = 2;
                    break;
                default:
                    // reconstruit...
                    if (longueurReconstruiteParcourue < seuilReconstructionAerienSouterrain){
                        // en aérien
                        this.modePoseSortie = 1; 
                    } else {
                        // en souterrain
                        this.modePoseSortie = 3; 
                    }
                    longueurRecP += this.longueur;
                    if((longueurRecP - this.longueur < seuilReconstructionAerienSouterrain) & (longueurRecP > seuilReconstructionAerienSouterrain)) {
                        System.out.println("Reconstruit en aérien : " + longueurRecP + " | Longueur de cette arête (" + this.id + ") :" + this.longueur);
                    }
                    break;
            }
            
        } else if (niveau < this.niveau){
            Queue<AreteBLOM> file = new ArrayBlockingQueue<>(filles.size(), true, filles);
            while(!file.isEmpty()){
                longueurRecP = file.remove().setModesPose(niveau, seuilReconstructionAerienSouterrain, longueurRecP);
            }
        }
        return longueurRecP;
    }
    
    //////////////////////////
    /// méthodes pour l'agrégation des UO dans les ZAPM et les UO de transport
    /////////////////////////////
    
    // fonction récursive
    public List<ZAPM> agregeResultats(String zone, double distance, Parametres parametres){
        List<ZAPM> res = new ArrayList<>();
        if (this.n.isPMExt(zone)){
            ZAPM zapm = new ZAPM(this.n, distance + this.longueur, zone, parametres);
            for (AreteBLOM fille : this.filles){
                fille.calculZAPM(zone, zapm, 0, parametres);
                zapm.addIncomingCables(fille, parametres);
            }
            res.add(zapm);
        }
        
        for (AreteBLOM fille : this.filles){
            res.addAll(fille.agregeResultats(zone, distance + this.longueur, parametres));
        }
        return res;
    }
    
    // fonction récursive
    private void calculZAPM(String zone, ZAPM zapm, double distancePM, Parametres parametres){
        zapm.addArete(this, parametres, distancePM);
        this.idPM.put(zone, zapm.getID());
        if (!this.n.isPMExt(zone)){
            for (AreteBLOM fille : this.filles){
                fille.calculZAPM(zone, zapm, distancePM+this.longueur, parametres);
            }
        }
    }
    
    // fonction récursive
    public List<Lineaires> getLineairesTransport(Parametres parametres, String zone){
        List<Lineaires> res = new ArrayList<>();
        res.add(this.getLineaires(parametres, false, zone));
        for (AreteBLOM a : this.filles){
            res.addAll(a.getLineairesTransport(parametres, zone));
        }
        return res;
    }
    
    //////////////////////////////////
    // méthodes pour le calcul des UO
    //////////////////////////////////
    
    // fonction récursive appelant des méthodes spécialisées pour le calcul de la demande, des cables, section totale, boitiers, épissures
    public void calculUO(Set<String> zones, Parametres parametres){
        for (AreteBLOM fille : filles){
            fille.calculUO(zones, parametres);
        }
        this.setDemande(zones, parametres);
        this.calculCables(zones, parametres);
        this.calculSectionGC(zones, parametres);
        this.calculBoitiersEpissures(zones, parametres);
    }
    
    // Les deux fonctions ci-après reprennent le principe de calculUO mais en
    // séparant demande, d'une part, et UO, d'autre part, afin de permettre
    // l'utilisation de la demande non mutualisée pour la détermination du mode de pose
    public void calculDemande(Set<String> zones, Parametres parametres){
        for (AreteBLOM fille : filles){
            fille.calculUO(zones, parametres);
        }
        this.setDemande(zones, parametres);
    }
    
    public void calculAutresUO(Set<String> zones, Parametres parametres){
        for (AreteBLOM fille : filles){
            fille.calculAutresUO(zones, parametres);
        }
        this.calculCables(zones, parametres);
        this.calculSectionGC(zones, parametres);
        this.calculBoitiersEpissures(zones, parametres);
    }
    
    private void setDemande(Set<String> zones, Parametres parametres){
        for (String zone : zones){
            int nonMutu = 0, mutu = 0, collecte = 0;
            for (AreteBLOM fille : filles){
                mutu+=fille.demandeFibresMutu.get(zone);
                nonMutu += fille.demandeFibresNonMutu.get(zone);
                collecte += fille.nbLiensCollectePM.get(zone);
            }
            if (n.isPMExt(zone)){
                mutu = 0;
                nonMutu += (int) Math.ceil(Math.max(n.taillePM(zone)*parametres.tauxCouplage(zone),parametres.nbFibresMin(zone)));
                collecte++;
            } else{
                mutu += n.demandeLocaleExt(zone);
            }
            if (zone.equals("ZTD_HD") && n.isPMint()){
                collecte+= 1;
                nonMutu+=n.demandeLocaleInt();
            }
            this.demandeFibresMutu.put(zone, mutu);
            this.demandeFibresNonMutu.put(zone, nonMutu);
            this.nbLiensCollectePM.put(zone, collecte);
            if(nonMutu > 0) this.hasNonMutu = true;
        }
    }
    
    private int demandeFibreCible(boolean isDistri, String zone, Parametres parametres){
        if (isDistri) return (int) Math.ceil(this.demandeFibresMutu.get(zone)*parametres.facteurSurcapaciteDistri);
        else return (int) Math.ceil(this.demandeFibresNonMutu.get(zone)*parametres.facteurSurcapaciteTransport);
    }
    
    private void calculCables(Set<String> zones, Parametres parametres) {
            this.nbCables = HashBasedTable.create(2,2);
            for (String zone : zones){
                this.nbCables.put(true, zone, cables(demandeFibreCible(true,zone, parametres), parametres));
                this.nbCables.put(false, zone, cables(demandeFibreCible(false,zone, parametres), parametres));
            }
    }
    
    private void calculSectionGC(Set<String> zones, Parametres parametres){
        double[] diametres = parametres.getDiametres(this.modePoseSortie);
        sections = HashBasedTable.create(2,2);
        sectionTotale = 0;
        for (String zone : zones){
            sections.put(Boolean.TRUE, zone, section(zone, true, diametres));
            sections.put(Boolean.FALSE, zone, section(zone, false, diametres));
            sectionTotale+=sections.get(Boolean.TRUE, zone);
            sectionTotale+=sections.get(Boolean.FALSE, zone);
        }
    }
    
    private double section(String zone, boolean isMutu, double[] diametres){
        double section = 0;
        int[] cables = this.nbCables.get(isMutu, zone);
        for (int i = 0;i<cables.length; i++){
            section+=cables[i]*Math.PI*Math.pow(diametres[i]/ (double) 2000, 2); // là on est en m^2
        }
        return section;
    }
    
    private int[] cables(double demande, Parametres parametres){
        int calibreMax = parametres.getCalibreMax(modePoseSortie);
        int calibreMin = parametres.getCalibreMin();
        int[] cables = new int[calibreMax+1];
        if (demande > 0) {
            for (int i = calibreMin;i<=calibreMax;i++){
                if (demande <= parametres.calibre[i]){
                    cables[i]++;
                    break;
                }   
            }
            if (demande > parametres.calibre[calibreMax]){
                cables[calibreMax] = (int) Math.ceil(demande / (double) parametres.calibre[calibreMax]);
            }
        }
        return cables;
    }
    
    private void calculBoitiersEpissures(Set<String> zones, Parametres parametres){
        this.boitiersEpissurage = HashBasedTable.create(2, 2);
        this.epissures = HashBasedTable.create(2, 2);
        this.distancesDernierBoitier = HashBasedTable.create(2, 2);
        boolean[] tiersExclu = {true, false};
        for (String zone : zones){
            for (boolean isDistri : tiersExclu){
                int[] cables = this.nbCables.get(isDistri, zone);
                int[] boitiers = new int[cables.length];
                int nbEpissures = 0;
                double distanceDernierBoitier = 0;
                
                if (this.nonVide(cables)){
                    // calcul des boitiers et epissures "intersection" s'il existe plusieurs arêtes filles
                    if(filles.size()>1){
                        int[] cablesAmonts = Arrays.copyOf(cables, cables.length); // on laisse une partie du (des) cable(s) en passage autant que faire se peut
                        for (int i=0; i<cables.length;i++){
                            boitiers[i] += cables[i];
                        }
                        nbEpissures = this.demandeFibreCible(isDistri, zone, parametres);
                        int indiceAmont = this.indiceNonNul(cablesAmonts);
                        for (AreteBLOM fille : filles){
                            int[] cablesFille = fille.nbCables.get(isDistri, zone);
                            if (indiceAmont < cablesFille.length){
                                int cablesEpisuresEvitees = Math.min(cablesAmonts[indiceAmont], cablesFille[indiceAmont]);                            
                                nbEpissures -= Math.min(cablesEpisuresEvitees*parametres.calibre[indiceAmont], fille.demandeFibreCible(isDistri, zone, parametres));
                                cablesAmonts[indiceAmont] -= cablesEpisuresEvitees;
                            }
                        }
                    }

                    // calcul des boîtiers et épissures supplémentaires en cas (de suite) d'arête(s) trop longue
                    distanceDernierBoitier = this.longueur;
                    if (this.filles.size() == 1) distanceDernierBoitier += this.filles.get(0).distancesDernierBoitier.get(isDistri, zone);
                    if(distanceDernierBoitier> parametres.seuil_boitier){// on rajoute un ou des boitier(s) quand c'est trop long 
                        int nbFois = (int) Math.floor(distanceDernierBoitier/parametres.seuil_boitier);
                        distanceDernierBoitier -= nbFois*parametres.seuil_boitier;
                        for (int i=0; i<cables.length;i++){
                            int nbBoitiers = (int) cables[i]*nbFois;
                            boitiers[i] += nbBoitiers;
                            nbEpissures += nbBoitiers*parametres.calibre[i];
                        }
                    }
                }
                
                // stockage des résultats
                this.boitiersEpissurage.put(isDistri, zone, boitiers);
                this.epissures.put(isDistri, zone, nbEpissures);
                this.distancesDernierBoitier.put(isDistri, zone, distanceDernierBoitier);
            }
        }
    }
    
    private boolean nonVide(int[] cables){
        for (int n : cables){
            if (n > 0) return true;
        }
        return false;
    }
    
    private int indiceNonNul(int[] cables){
        for (int i = 0;i<cables.length;i++){
            if (cables[i] > 0) return i;
        }
        System.out.println("Tableau vide");
        return 0;
    }
    
    /////////////////////////////////////
    /// méthodes de communication des UO
    /////////////////////////////////////
    
    public Lineaires getLineaires(Parametres parametres, boolean isDistri, String zone){
        return new Lineaires(parametres, this.modePoseSortie, this.longueur, this.nbCables.get(isDistri, zone), this.boitiersEpissurage.get(isDistri,zone), this.epissures.get(isDistri, zone), this.sections.get(isDistri, zone), this.sectionTotale);
    }
    
    public int[] getCables(boolean isDistri, String zone){
        return this.nbCables.get(isDistri, zone);
    }

    public int getNbFibres(boolean isDistri, String zone, int[] calibre, int calibreMin){
        int[] cables = this.nbCables.get(isDistri, zone);
        int n = Math.min(calibre.length, cables.length);
        if (calibre.length < cables.length)
            System.out.println("Problème avec les câbles ! Le tableau est trop grand");
        int res = 0;
        for (int i =0;i<n;i++){
            res+=calibre[calibreMin+i]*cables[i];
        }
        return res;
    }
    
    //////////////////////////////////////////////////////
    /// méthodes pour l'impression de shapefiles représentant le réseau modélisé
    //////////////////////////////////////////////////////
    
    public static SimpleFeatureType getAreteFeatureType(CoordinateReferenceSystem crs){
        SimpleFeatureTypeBuilder builderLineaires = new SimpleFeatureTypeBuilder();
        builderLineaires.setName("ARETE");
        builderLineaires.setCRS(crs);
        builderLineaires.add("the_geom", MultiLineString.class);
        builderLineaires.add("ID", Integer.class);
        builderLineaires.add("LONGUEUR", Double.class);
        builderLineaires.add("MODE_POSE", Integer.class);
        builderLineaires.add("MODE_INIT", Integer.class);
        builderLineaires.length(4).add("ZONE_MACRO", String.class);
        builderLineaires.length(13).add("NRO", String.class);
        builderLineaires.length(3).add("TYPE", String.class);
        builderLineaires.add("T_LIGNES", Integer.class);
        builderLineaires.add("T_FIBRES", Integer.class);
        builderLineaires.add("D_ID_PM300", Integer.class);
        builderLineaires.add("D_ID_PM100", Integer.class);
        builderLineaires.add("D_LIGNES", Integer.class);
        builderLineaires.add("D_FIBRES", Integer.class);
        return builderLineaires.buildFeatureType();
    }
    
    
    // fonction récursives pour renvoyer les features des arêtes de l'arbre sous this (exclu pour la root)
    public List<SimpleFeature> getFeatures(String CodeNRO, String zoneMacro, Set<String> zones, int[] calibres, GeometryFactory gf,SimpleFeatureBuilder featureBuilderLineaires){
        
        List<SimpleFeature> aretes = new ArrayList<>();
        
        // calculs préliminaires pour la création du feature de l'arête
        int lignesDistri = 0, lignesTransport = 0, fibresDistri = 0, fibresTransport = 0;
        for (String zone : zones){
            lignesDistri += this.demandeFibresMutu.get(zone);
            lignesTransport += this.demandeFibresNonMutu.get(zone);
            fibresDistri += produit(this.nbCables.get(true, zone), calibres);
            fibresTransport += produit(this.nbCables.get(false, zone), calibres);
        }
        String type = "";
        if (lignesTransport > 0){
            type = "T";
            if (lignesDistri > 0)
                type+="+D";
        } else if (lignesDistri > 0)
            type = "D";

        // création du feature de cette arête
        featureBuilderLineaires.add(gf.createMultiLineString(new LineString[]{gf.createLineString(this.points)}));
        featureBuilderLineaires.add(this.id);
        featureBuilderLineaires.add(this.longueur);
        featureBuilderLineaires.add(this.modePoseSortie);
        featureBuilderLineaires.add(this.modePose);
        featureBuilderLineaires.add(zoneMacro);
        featureBuilderLineaires.add(CodeNRO);
        featureBuilderLineaires.add(type);
        featureBuilderLineaires.add(lignesTransport);
        featureBuilderLineaires.add(fibresTransport);
        if (zoneMacro.equals("ZTD")){
            featureBuilderLineaires.add(this.idPM.get("ZTD_BD"));
            featureBuilderLineaires.add(this.idPM.get("ZTD_HD"));
        } else{
            featureBuilderLineaires.add(this.idPM.get("ZMD"));
            featureBuilderLineaires.add("");
        }
        featureBuilderLineaires.add(lignesDistri);
        featureBuilderLineaires.add(fibresDistri);
        aretes.add(featureBuilderLineaires.buildFeature(null));
        
        // récursion
        for (AreteBLOM a : this.filles){
            aretes.addAll(a.getFeatures(CodeNRO, zoneMacro, zones, calibres, gf, featureBuilderLineaires));  
        }
        return aretes;
    }
    
    public static SimpleFeatureType getPBOFeatureType(CoordinateReferenceSystem crs, String zoneMacro){
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("PBO");
        builder.setCRS(crs);
        builder.add("the_geom", Point.class);
        builder.length(4).add("ZONE_MACRO", String.class);
        builder.length(13).add("ID_NRO", String.class);
        if (zoneMacro.equals("ZTD")){
            builder.add("ID_PM300", String.class);
            builder.add("ID_PM100", String.class);
        } else {
            builder.add("ID_PM", String.class);
        }
        builder.add("ID_PBO", String.class);
        builder.add("TYPE", String.class);
        builder.add("NB_LIGNES", Integer.class);
        return builder.buildFeatureType();
    }
    
    public List<SimpleFeature> getFeaturesPBO(String zoneMacro, String idNRO, Set<String> zones, GeometryFactory gf, SimpleFeatureBuilder featureBuilder){
        List<SimpleFeature> listePBO = new ArrayList<>();
        int totaleDemandeLocaleExt = 0;
        for (String zone : zones){
            totaleDemandeLocaleExt += this.n.demandeLocaleExt(zone);
        }
        if (totaleDemandeLocaleExt > 0){
            featureBuilder.add(gf.createPoint(new Coordinate(this.n.coord[0], this.n.coord[1])));
            featureBuilder.add(zoneMacro);
            featureBuilder.add(idNRO);
            if (zoneMacro.equals("ZTD")){
                    featureBuilder.add(this.idPM.get("ZTD_BD"));
                    featureBuilder.add(this.idPM.get("ZTD_HD"));
                } else{
                    featureBuilder.add(this.idPM.get("ZMD"));
                }
            featureBuilder.add(this.n.id);
            featureBuilder.add(this.modePoseSortie);
            featureBuilder.add(totaleDemandeLocaleExt);
            listePBO.add(featureBuilder.buildFeature(null));
        }
        for (AreteBLOM a : this.filles){
            listePBO.addAll(a.getFeaturesPBO(zoneMacro, idNRO, zones, gf, featureBuilder));
        }
        return listePBO;
    }
 
    private int produit(int[] tabA, int[] tabB){
        int res = 0;
        for(int i = 0;i<tabA.length;i++){
            res += tabA[i]*tabB[i];
        }
        return res;
    }
}
