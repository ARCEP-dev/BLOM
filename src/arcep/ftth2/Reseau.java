/*
 * Copyright (c) 2024, Autorité de régulation des communications électroniques, des postes et de la distribution de la presse
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.util.factory.Hints;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.Pseudograph;
import org.jgrapht.graph.WeightedPseudograph;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import arcep.utilitaires.DijkstraReseau;
import arcep.utilitaires.GraphConnector;
import de.biomedical_imaging.edu.wlu.cs.levy.CG.KDTree;
import de.biomedical_imaging.edu.wlu.cs.levy.CG.KeyDuplicateException;

public class Reseau {
   
    private final String id;
    private NoeudAGarder centre;    // Il s'agit du NRO du réseau
    private final Pseudograph<NoeudAGarder,Arete> graphe; // undirected, loops and edge multiplicity are permitted
    private int compteurArete;
    private int compteurNoeud;
    private final Set<String> zones; // ZTD_HD, ZTD_BD, ZMD_AMII ou ZMD_RIP
    //String zone; // ZTD, ZMD_AMII ou ZMD_RIP
    private final KDTree<Node> index_GC_propre = new KDTree<>(2);
    private final KDTree<Node> index_GC_voisin = new KDTree<>(2);
    private final KDTree<Node> index_routier_propre = new KDTree<>(2);
    private final KDTree<Node> index_routier_voisin = new KDTree<>(2);
    // remarque importante : les indices plus petits sont "dominants" sur les indices plus grands
    // exemple : si un Noeud peut être qualifié de GC propre et routier voisin, alors il est GC propre
    
    private final double toleranceNoeud;
    private final double seuilToleranceGC2;
    
    private final double coefVdO;
    
    // ces facteurs jouent un rôle en fonctionnement "standard" (avec le GC d'Orange),
    // pas en fonctionnement "dégradé" quand seul les réseaux routiers sont utilisés
    // pour le tracé des réseaux
    // Les valeurs sont initialisées via le fichier BLOM.ini
    private final double facteurConduite;
    private final double facteurAerien;
    private final double facteurPleineTerre;
    
    protected DijkstraReseau dijkstraReseau;
    
    public Reseau(String id, String zone, Map<String, Double> parametresReseau){
        this.id = id;
        graphe = new WeightedPseudograph<>(Arete.class);
        compteurArete = 0;
        compteurNoeud = 0;
        zones = new HashSet<>();
        
        this.toleranceNoeud = parametresReseau.get("toleranceNoeud");
        this.seuilToleranceGC2 = parametresReseau.get("seuilToleranceGC2");
        
        this.facteurConduite = parametresReseau.get("facteurConduite");
        this.facteurAerien = parametresReseau.get("facteurAerien");
        this.facteurPleineTerre = parametresReseau.get("facteurPleineTerre");
        
        this.coefVdO = parametresReseau.get("facteurVdO");
        System.out.println("Création du réseau du NRO "+id);
    }
    
    protected Pseudograph<NoeudAGarder, Arete> getGraphe() {
		return graphe;
	}
    
    public void initDijkstra() {
    	if (dijkstraReseau != null) {
    		throw new RuntimeException("On ne peut initialiser le dijkstra du reseau qu'une seul fois");
    	}
    	if (centre == null) {
    		throw new RuntimeException("On ne peut initialiser le dijkstra du reseau qu'après avoir setté le centre");
    	}
    	this.dijkstraReseau = new DijkstraReseau(graphe, centre);
    }
    
    public void loadLineString(double[] coord1, double[] coord2, String type, boolean voisin, int modePose, double longueur, long idProprio, String nature, List<double[]> intermediaires){
        if (type.equals("GC") || nature.startsWith("Route") || nature.equals("Rond-point")){ // on évite les chemins, sentiers, bacs piétons, etc
            
            
            double[] coord;
            Node tempNode;
            
            // On établit la liste des noeuds intermédiaires qui se trouvent à l'emplacement
            // d'un Node d'une autre Arete et deviendront ainsi des NoeudAGarder
            // (en découpant l'Arete ajoutée en autant de sous-aretes)
            List<Integer> internesAGarder = new ArrayList<Integer>(); // la liste contient le numéro de l'élément de intermediaires à conserver en tant que NoeudAGarder et le numéro du premier élément d'intermediaire qui deviendra un NoeudInterne de l'arête dont ce NoeudAGarder est la 2nde extrémité
            for (int i = 0; i < intermediaires.size(); i++) {
                coord = intermediaires.get(i);
                // si on rencontre sur le tracé des Node existants dans d'autres
                // infrastructures, on enregistre leur position
                // On exclut néanmoins les noeuds internes trop proches
                // du noeud 1 et du noeud 2 (non encore ajoutés au graphe et aux index)
                
                // Création d'un Node pour calculer sa distance avec les extrémités
                tempNode = new Node(); tempNode.coord = coord;
                if(tempNode.distance(coord1) > toleranceNoeud && tempNode.distance(coord2) > toleranceNoeud) {
                    // N.B. que la méthode isAnyNodeOutThere transforme le Node tiers
                    // en NoeudAGarder s'il n'en est pas déjà un (via Reseau.keep(NoeudInterne))
                    if(isAnyNodeOutThere(coord, indice(type, voisin))){
                        internesAGarder.add(i);
                    }
                }
            }
            
            // On ajoute les NoeudAGarder et les (sous-)Arete
            // Ajout du noeud 1 (coord1) et initialisation de precedent avec le premier noeud
            this.isAnyNodeOutThere(coord1, indice(type, voisin));
            NoeudAGarder precedent = this.getNoeud(coord1, indice(type, voisin));
            NoeudAGarder courant;
            int iPrec = -1;
            
            double longParcourue = 0;   // Longueur de la sous-arête
            @SuppressWarnings("unused")
			double longParcourueTotale = 0; // Pour contrôler qu'on retombe bien, au total, sur la "longueur"
            
            // Ajout des NoeudInterne qui doivent être convertis en NoeudAGarder
            // et des Aretes (avec calcul de leur longueur)
            for (int iAGarder : internesAGarder ){
                // Calcul de la longueur de l'arête
                for (int i = iPrec+1; i <= iAGarder; i++) {
                    // Coordonnées du noeud intermédiaire
                    coord = intermediaires.get(i);
                    // Création d'un Node pour pouvoir calculer sa distance avec le précédent
                    tempNode = new Node(); tempNode.coord = coord;
                    if(i > iPrec+1) longParcourue += tempNode.distance(intermediaires.get(i-1));
                    else longParcourue += tempNode.distance(precedent);// distance au tout premier noeud
                }
                
                // Ajout du NoeudAGarder et de l'Arete
                courant = this.getNoeud(intermediaires.get(iAGarder), indice(type, voisin));
                
                this.addArete(
                    precedent,
                    courant,
                    modePose, Parametres.arrondir(longParcourue, 1), idProprio,
                    intermediaires.subList(iPrec+1, iAGarder), // nb. que subList(from, to) inclue from et exclue to
                    type, voisin);
                
                longParcourueTotale += longParcourue;
                longParcourue = 0;
                precedent = courant;
                iPrec = iAGarder;
            }
            
            // Ajout du dernier noeud et de la dernière arrête
            this.isAnyNodeOutThere(coord2, indice(type, voisin));
            NoeudAGarder n2 = this.getNoeud(coord2, indice(type, voisin));
            
            // Calcul de la longueur de la dernière (sous-)arête
            if (intermediaires.size() > 0) {
                for (int i = iPrec+1; i < intermediaires.size(); i++) {
                    // Coordonnées du noeud intermédiaire
                    coord = intermediaires.get(i);
                    // Création d'un Node pour pouvoir calculer sa distance avec le précédent
                    tempNode = new Node(); tempNode.coord = coord;
                    if(i > iPrec+1) longParcourue += tempNode.distance(intermediaires.get(i-1));
                    else longParcourue += tempNode.distance(precedent);// distance au tout premier noeud
                }
            
                longParcourue += n2.distance(intermediaires.get(intermediaires.size() - 1));
                longParcourueTotale += longParcourue;
            } else {
                longParcourue = n2.distance(precedent);
                longParcourueTotale = longParcourue;
            }
            
            this.addArete(precedent,
                    n2,
                    modePose, Parametres.arrondir(longParcourue, 1), idProprio,
                    intermediaires.subList(iPrec+1, intermediaires.size()), // on ne conserve que les "derniers" noeuds intermédiaires
                    type, voisin);
        }
    }
    
    /*
    * Placement du NRO au point du réseau d'infrastructures physiques 
    * le plus proche du NRA dont il est issu
    */
    public void setCentre(double x, double y){
        double[] coord = {x,y};
        centre = this.getNoeud(coord, 0);
    }
    
    /*
    * Connexité du réseau de la ZANRO
    * Extrait de la CP d'avril 2017 (p. 30) :
    * "ajoute les arêtes les plus petites possibles pour assurer
    * la connexité du réseau, en partant de la composante connexe du NRO
    * et en reliant le point le plus proche hors de celle-ci
    * tant que le graphe représentant le réseau n’est pas connexe."
    *
    * retourne un tableau contenant :
    * [0] le nombre d'arêtes ajoutées
    * [1] la longueur cumulée des aêtes ajoutées
    */
    public double[] forceConnexite(){
        double[] res = {0,0};
        ConnectivityInspector<NoeudAGarder, Arete> ci = new ConnectivityInspector<>(graphe);
        System.out.println("Début de la complétion de " + ci.connectedSets().size() + " ilots");
        while (!ci.isConnected()) {
            Set<NoeudAGarder> noeuds = new HashSet<>();
            List<Set<NoeudAGarder>> ilots = ci.connectedSets();
            
            Set<NoeudAGarder> ilotCentral = ci.connectedSetOf(centre);
            for (Set<NoeudAGarder> ilot : ilots){
                if (!ilot.equals(ilotCentral)) noeuds.addAll(ilot);
            }
            List<NoeudAGarder> extremites = this.trouverPont(ilotCentral, noeuds);
            double longueur = extremites.get(0).distance(extremites.get(1));
            this.construitAreteConnexite(extremites.get(0), extremites.get(1), longueur);
            res[0]++;
            res[1]+= longueur;
            ci.edgeRemoved(null); // Réinitialisation du ConnectivityInspector après ajout de l'arête
        }
        return res;
    }
    
    public double[] forceConnexiteV2(){
        double[] res = {0,0};
        GraphConnector connector = new GraphConnector(graphe, centre);
            
        for (var extremites : connector.getAreteToMakeConnexe()) {
            double longueur = extremites.get(0).distance(extremites.get(1));
            this.construitAreteConnexite(extremites.get(0), extremites.get(1), longueur);
            res[0]++;
            res[1]+= longueur;
        }
        return res;
    }
    
    
    /**
     * Parcourt l'ensemble des NoeudAGarder du réseau afin de raccorder ceux qui
     * appartiennent aux indices de routes au réseau de GC, de manière à rendre
     * plus nombreux les chemins possibles lors de la recherche de plus court chemin
     * et d'éviter d'utiliser de la route et du GC en parallèle sur des sections
     * longues.
     * 
     * Le raccordement des route au GC n'est effectué que lorsque la simulation
     * utilise du GC, sinon elle crée un pont en ligne droite entre chaque
     * NoeudAGarder et le NRO (qui fait toujours partie de l'indice GC)
     * 
     * @return un array contenant le nombre d'arêtes ajoutées et leur longueur
     * totale. Les arêtes sont créées avec le modePose 14
     */

    public double[] connecteRoutes(){
        
        // res[1] est le nombre d'arêtes ajoutées et res[2] leur longueur cumulée
        double[] res = {0, 0};
        
        Object[] plusProcheNodeGC;
        
        Node nodeGC;
        NoeudAGarder noeudGC;
        double distGC;
        
        if (index(0).size() + index(1).size() > 0) {
            
            // il est nécessaire récupérer la liste des  le graphe car la boucle est susceptible
            // d'ajouter des NoeudsAGarder, créant une erreur
            // java.util.ConcurrentModificationException
            NoeudAGarder[] vertices = graphe.vertexSet().toArray(new NoeudAGarder[graphe.vertexSet().size()]);
            
            for (NoeudAGarder n : vertices) {
                
                // on cherche si le noeud existe en tant que noeud des indices GC
                if(!isInIndice(n, 0) && !isInIndice(n, 1)) {
                    // Si ce n'est pas le cas, on cherche le noeud de GC le plus proche
                    // et on crée l'arête le reliant au noeud routier n
                    plusProcheNodeGC = getPlusProcheNodeGC(n.coord);
                    nodeGC = (Node) plusProcheNodeGC[0];
                    distGC = (double) plusProcheNodeGC[1];
                    
                    if (distGC < seuilToleranceGC2) {
                        res[0] = res[0] + 1;
                        res[1] = res[1] + distGC;

                        // si le noeud GC le plus proche trouvé est un NoeudInterne, il
                        // est conservé (méthode keep())
                        if (nodeGC instanceof NoeudInterne) {
                            noeudGC = keep((NoeudInterne) nodeGC, (int) plusProcheNodeGC[2], false);
                        } else {
                            noeudGC = (NoeudAGarder) nodeGC;
                        }

                        // une arête est créée entre le NoeudAGarder routier et le Node GC
                        this.construitAreteConnecteRoute(n, noeudGC, distGC);
                    }
                }
            }
        }
        
        return res;
    }
    
    /*
    * Procédure d'association de la demande locale et des réseaux physique
    * Extrait de la CP d'avril 2017 :
    *   "L’approche finalement retenue (décrite au b)) teste pour chaque PC
    *   du réseau cuivre les noeuds les plus proches au sein des réseaux
    *   physiques, et choisit parmi ceux-ci celui correspondant à la plus petite
    *   distance totale NRO-PBO. Pour limiter les risques de chemins
    *   non pertinents, la recherche des noeuds les plus proches du PC se fait
    *   au sein de l’ensemble des points intermédiaires décrivant le tracé
    *   des arêtes du réseau d’infrastructures physiques et pas seulement sur
    *   les extrémités des arêtes. Les calculs de plus court chemin se font
    *   en utilisant l’algorithme de Dijkstra."
    */
    public double[] addPC(double x, double y, String zone, int nbLignes, boolean isPMint){
        double[] coord = {x, y};
        double[] areteAjoutee = {0, 0};
       
        // Calcul du plus court chemin par le GC d'Orange
        Object[] gc = this.distanceAuCentre(coord, "GC");
        Node n = (Node) gc[1];  // n est le Node auquel relier le PC pour construire ce plus court chemin
        
        NoeudAGarder noeud; // Le noeud qui portera la demande correspondant au PC
        
        double distGC = n.distance(coord);  // distance séparant le PC du Node n
        
        // Rq : toleranceNoeud est la valeur du champ "Tolérance pour l'accrochage des noeuds"
        if (distGC < toleranceNoeud){
            // Dans ce cas on considère que la demande sera rattachée au Node n (le PC est "accroché" au Node n)
            
            // n est soit un NoeudAGarder, soit un NoeudInterne (intermédiaire)
            // Si le noeud n est un noeud intermédiaire, il est transformé en NoeudAGarder
            if (n instanceof NoeudInterne) noeud = this.keep((NoeudInterne) n,(Integer) gc[2], true);
            // Sinon noeud prend les attributs de n forcé dans le type NoeudAGarder
            else noeud = (NoeudAGarder) n;
            areteAjoutee[0] = 0;
            areteAjoutee[1] = distGC;
            
        	this.dijkstraReseau.recompute(noeud); // pour s'assurer que le pathToCentre existe (il faut bien ajouter les arêtes d'abord))
            if (noeud.distanceAuCentre == 0) noeud.distanceAuCentre = noeud.meilleurChemin.distanceAuCentre;
        	this.dijkstraReseau.ajoutFourreauxSiNecessaire(this, noeud);
        } else{
            // si les routes ne sont pas autorisées, on construit le nouveau noeud dans l'index GC
            if (index(2).size() == 0) {
                // dans ce cas le seuilToleranceGC2 n'a pas de sens, on crée dans tous les cas un nouveau NoeudAGarder est créé, aux coordonnées coord
                noeud = this.getNoeud(coord, (Integer) gc[2]);

                // Si le Node n est un noeud intermédiaire (NoeudInterne), on le transforme en NoeudAGarder
                if (n instanceof NoeudInterne) n = keep((NoeudInterne) n,(Integer) gc[2], true);

                // L'arete correspondante est construite, c'est-à-dire ajoutée
                // avec un modePose correspondant à du GC reconstruit
                // et la longueur correspondant à la distance à vol d'oiseau à couvrir
                this.construitAretePC(noeud, (NoeudAGarder) n, distGC);

                // On s'assure que que le pathToCentre existe (il est construit dans le cas contraire)
                this.dijkstraReseau.recompute((NoeudAGarder) n, noeud); // pour s'assurer que le pathToCentre existe (il faut bien ajouter les arêtes d'abord))
                if (noeud.distanceAuCentre == 0) noeud.distanceAuCentre = noeud.meilleurChemin.distanceAuCentre;
            	this.dijkstraReseau.ajoutFourreauxSiNecessaire(this, noeud);

                areteAjoutee[0] = 1;
                areteAjoutee[1] = distGC;
            } else {

                // sinon on tente de construire le plus court chemin en s'autorisant
                // l'utilisation des routes
                Object[] routier = this.distanceAuCentre(coord, "routier");
                
                // Rq : seuilToleranceGC2 est la valeur du champ "Tolérance de distance pour etre raccordé au GC"
                if (distGC < this.seuilToleranceGC2 && (Double) gc[0] < (Double) routier[0]){
                    // Ici la distance par le GC est quand meme plus avantageuse
                    // On va donc conserver le chemin par le GC mais le PC est trop
                    // loin du Node n, donc on rajoute une arete

                    // Un nouveau NoeudAGarder est créé, aux coordonnées coord
                    noeud = this.getNoeud(coord, (Integer) gc[2]);

                    // Si le Node n est un noeud intermédiaire (NoeudInterne), on le transforme en NoeudAGarder
                    if (n instanceof NoeudInterne) n = keep((NoeudInterne) n,(Integer) gc[2], true);

                    // L'arete correspondante est construite, c'est-à-dire ajoutée
                    // avec un modePose correspondant à du GC reconstruit
                    // et la longueur correspondant à la distance à vol d'oiseau à couvrir
                    this.construitAretePC(noeud, (NoeudAGarder) n, distGC);

                    // On s'assure que que le pathToCentre existe (il est construit dans le cas contraire)
                	this.dijkstraReseau.recompute((NoeudAGarder) n, noeud); // pour s'assurer que le pathToCentre existe (il faut bien ajouter les arêtes d'abord))
                    if (noeud.distanceAuCentre == 0) noeud.distanceAuCentre = noeud.meilleurChemin.distanceAuCentre;
                	this.dijkstraReseau.ajoutFourreauxSiNecessaire(this, noeud);

                    areteAjoutee[0] = 1;
                    areteAjoutee[1] = distGC;
                } else{
                    // on choisit comme noeud de rattachement le noeud minimisant
                    // la distance au centre en s'autorisant l'utilisation des routes
                    n = (Node) routier[1];
                    
                    // Si le noeud n est un noeud intermédiaire (NoeudInterne), on le transforme en NoeudAGarder
                    if (n instanceof NoeudInterne) n = keep((NoeudInterne) n,(Integer) routier[2], true);
                    double distRoutier = n.distance(coord);
                    
                    // Dans ce cas on considère que la demande sera rattachée au Node n (le PC est "accroché" au Node n)
                    if(distRoutier < toleranceNoeud) {
                        noeud = (NoeudAGarder) n;
                        areteAjoutee[0] = 0;
                        areteAjoutee[1] = distRoutier;
                        
                    	this.dijkstraReseau.recompute(noeud); // pour s'assurer que le pathToCentre existe (il faut bien ajouter les arêtes d'abord))
                        if (noeud.distanceAuCentre == 0) noeud.distanceAuCentre = noeud.meilleurChemin.distanceAuCentre;
                    	this.dijkstraReseau.ajoutFourreauxSiNecessaire(this, noeud);
                    }
                    
                    else{
                        // le PC est trop loin du Node n, donc on rajoute une arete

                        // Un nouveau NoeudAGarder est créé, aux coordonnées coord
                        noeud = this.getNoeud(coord, (Integer) routier[2]);

                        // L'arete correspondante est construite, c'est-à-dire ajoutée
                        // avec un modePose correspondant à du GC reconstruit
                        // et la longueur correspondant à la distance à vol d'oiseau à couvrir
                        this.construitAretePC(noeud, (NoeudAGarder) n, distRoutier);

                        // On s'assure que que le pathToCentre existe (il est construit dans le cas contraire)
                    	this.dijkstraReseau.recompute((NoeudAGarder) n, noeud); // pour s'assurer que le pathToCentre existe (il faut bien ajouter les arêtes d'abord))
                        if (noeud.distanceAuCentre == 0) noeud.distanceAuCentre = noeud.meilleurChemin.distanceAuCentre;
                    	this.dijkstraReseau.ajoutFourreauxSiNecessaire(this, noeud);

                        areteAjoutee[0] = 1;
                        areteAjoutee[1] = distRoutier;
                    }
                }
            }
        }
        if (zone.equals("ZTD_HD") && isPMint) noeud.declarePMint();
        noeud.addDemande(zone, nbLignes);   // La demande du PC est ajoutée au NoeudAGarder noeud
        zones.add(zone);

        return(areteAjoutee);
    }

    public void test(){
        System.out.println("Nombre de noeuds dans le graphe : " + graphe.vertexSet().size());
        System.out.println("Nombre d'arêtes dans le graphe : " + graphe.edgeSet().size());
        
        System.out.println("Taille de l'index GC propre : "+index(0).size());
        System.out.println("Taille de l'index GC voisin : "+index(1).size());
        System.out.println("Taille de l'index routier propre : "+index(2).size());
        System.out.println("Taille de l'index routier voisin : "+index(3).size());
        
    }
    
    public void store(File dossier){
        try{
            // écriture du fichier contenant les arêtes
            // Rq : les x,y des points intermédiaires sont écrits à la suite sur la ligne correspondant à l'arête sur laquelle ils se situent
            PrintWriter writer = new PrintWriter(dossier+"/Aretes_"+id+".csv");
            writer.println("Identifiant;Noeud1;Noeud2;Mode de pose;Longueur;Proprietaire;Points intermediaires");
            for (Arete a : graphe.edgeSet()){
            	var node1 = graphe.getEdgeSource(a);
            	var node2 = graphe.getEdgeTarget(a);
            	if (node1.getId() < node2.getId()) {
            		a.print(writer, node1, node2);
            	} else {
            		a.print(writer, node2, node1);
            	}
                
            }
            writer.close();
            
            // écriture du fichier contenant les noeuds
            // Rq : la première ligne contient l'id du noeud-centre et la zone
            // Rq2 : si le noeud est extrémité d'arêtes, es id de celles-ci sont écrites à la suite sur la ligne correspondant au noeud
            writer = new PrintWriter(dossier+"/Noeuds_"+id+".csv");
            writer.print("Id du centre;"+centre.id+";Zones:;"+zones.size());
            for (String zone : zones){
                writer.print(";"+zone);
            }
            writer.println();
            writer.println("Identifiant;X;Y;Nombre de zone de demande locale;Demande locale par zone;Indice PM;Nombre d'arêtes dans le path;Identifiants des arêtes");
            for (NoeudAGarder n : graphe.vertexSet()){
            	if (n.hasDemandeLocale()) n.setPath(n.getPathFromMeilleurResultat());
                n.print(writer);
            }
            writer.close();
            
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public static Map<Integer,Noeud> readNoeudsSelectDemande(String dossier, String codeNRO, List<Noeud> demande, int seuilPMint){
        Map<Integer,Noeud> noeuds = new HashMap<>();
        try{
            BufferedReader reader = new BufferedReader (new FileReader(dossier+"/Noeuds_"+codeNRO+".csv"));
            reader.readLine();
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null){
                String fields[] = line.split(";");
                Noeud n = new Noeud(line.split(";"), seuilPMint);
                noeuds.put(Integer.parseInt(fields[0]),n);
                if (n.demandeLocaleTotale()>0){
                    demande.add(n);
                }
            }
            reader.close();
        } catch (IOException e){
            e.printStackTrace();
        }
        return noeuds;
    }
    
    /*
    * Lit la 1ère colonne (sauf les 2 premières lignes) du fichier intermédiaire
    * BLO[-GC][-Routier]-[xx]km/../Noeuds_[codeNRO].csv
    * récupérer les noeuds et crée un tableau associant un ID un un Noeud
    * ID du noeud : Noeud
    */
    public static Map<Integer,Noeud> readNoeuds(String dossier, String codeNRO, int seuilPMint){
        Map<Integer,Noeud> map = new HashMap<>();
        try{
            BufferedReader reader = new BufferedReader (new FileReader(dossier+"/Noeuds_"+codeNRO+".csv"));
            reader.readLine();
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null){
                String fields[] = line.split(";");
                map.put(Integer.parseInt(fields[0]), new Noeud(fields, seuilPMint));
            }
            reader.close();
        } catch (IOException e){
            e.printStackTrace();
        }
        return map;
    }
    
    /*
    * Lit la 1ère ligne du fichier intermédiaire BLO[-GC][-Routier]-[xx]km/../Noeuds_[codeNRO].csv
    * pour déterminer le code identifiant le NRO (Centre)
    */
    public static int readCentre(String dossier, String codeNRO){
        int i = -1;
        try{
            BufferedReader reader = new BufferedReader (new FileReader(dossier+"/Noeuds_"+codeNRO+".csv"));
            String line = reader.readLine();
            i = Integer.parseInt(line.split(";")[1]);
            reader.close();
        } catch (IOException e){
            e.printStackTrace();
        }
        return i;
    }

    public static Map<Long,Arete> readAretes(String dossier, String codeNRO){
        Map<Long,Arete> map = new HashMap<>();
        try{
            BufferedReader reader = new BufferedReader (new FileReader(dossier+"/Aretes_"+codeNRO+".csv"));
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null){
                String[] fields = line.split(";");
                map.put(Long.parseLong(fields[0]), new Arete(fields));
            }
            reader.close();
        } catch (IOException e){
            e.printStackTrace();
        }
        return map;
    }
    
    /*
    * Renvoie l'index (KDTree) référençant les noeuds correspondant à l'indice
    * indiqué, qui définit l'ordre de priorité dans lequel on recherche le noeud
    * le plus proche d'un couple de coordonnées
    * (cf. méthode indice() )
    */
    protected KDTree<Node> index(int indice){
        switch(indice){
            case 0:
                return index_GC_propre;
            case 1:
                return index_GC_voisin;
            case 2:
                return index_routier_propre;
            case 3:
                return index_routier_voisin;
            default:
                return null;
        }
    } 
    
    /*
    * Indique l'ordre de priorité des différents indexes de noeuds :
    * 0) noeuds dont une arête est une arête de GC réutilisable du département
    * 1) noeuds qui ne sont pas dans l'index précédent mais dont au moins
    *   une arête est une arête de GC réutilisable d'un département voisin
    * 2) noeuds qui ne sont pas dans les index précédents et dont au moins
    *   une arête est une arête du réseau routier du département
    * 3) les autres noeuds
    */
    private int indice(String type, boolean voisin){
        int i = -1;
        switch(type){
            case "GC":
                if (voisin) i = 1;
                else i = 0;
                break;
            case "routier":
                if (voisin) i = 3;
                else i = 2;
                break;
        }
        return i;
    }
    
    public String type(int indice){
        switch(indice){
            case 0:
            case 1:
                return "GC";
            case 2:
            case 3:
                return "routier";
            default:
                return "ERROR";
        }
    }
    
    /**
     * Teste l'existence d'un Node à une coordonnée donnée (avec une tolérance
     * égale à toleranceNoeud) dans les index jusqu'à iMax. Renvoie True si un
     * tel Node est trouvé. S'il s'agit d'un NoeudInterne, il est converti en
     * NoeudAGarder grâce à la méthode keep().
     * 
     * @param coord (double[x, y]) les coordonnées auxquelles on recherche un Node
     * @param iMax (int 0 - 3) l'indice max de l'index dans lequel on recherche un Node
     * @return (boolean) true s'il existe un Node proche de ces coordonnées, false sinon
     */
    private boolean isAnyNodeOutThere(double[] coord, int iMax) {
        Node n = null;
        try{
            // on cherche si un noeud existe déjà à cet emplacement dans les index jusqu'au "sien" (le remplissage se faisant dans cet ordre)
            for (int i = 0; i <= iMax ;i++){
                n = index(i).search(coord);
                if (n != null) return true;
                
            }
            // si on n'a rien trouvé à ces coordonnées précises, on cherche un noeud proche dans les index
            for (int i = 0; i <= iMax;i++){
                if (index(i).size() > 0) n = index(i).nearest(coord);
                else n = null;
                if (n != null && n.distance(coord) < toleranceNoeud){
                    // S'il s'agit d'un NoeudInterne, il est transformé en NoeudAGarder
                    // (et l'Arete sur laquelle il se situe coupée en deux)
                    if (n instanceof NoeudInterne) this.keep((NoeudInterne) n, i, false);
                    return true;
                }
            }
        } catch(Exception e){
            e.printStackTrace();
        }
        return false;
    }
    
    /*
    * Indique si un Node existe déjà dans l'indice spécifié
    */
    private boolean isInIndice(Node n, int i) {
        
        Node foundNode = null;
        
        try{
            foundNode = index(i).search(n.coord);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return foundNode != null;
    }
    

    /**
     * Trouve le NoeudAGarder le plus proche des coordonnées indiquées
     * en distance à vol d'oiseau (Node.distance()) au sein des indices de GC
     * (retourne null si ces indices sont vides)
     * 
     * @param coord (double[]) coordonnées X, Y à partir desquelles rechercher
     * @return Array contenant trois éléments :
     *      [0] : Node de GC le plus proche
     *      [1] : distance au Node de GC le plus proche
     *      [2] : indice du kdTree auquel le Node appartient
     */
    private Object[] getPlusProcheNodeGC(double[] coord) {
        
        Object[] result = new Object[3];
        
        Node tempNode = null;
        Node closestNode = null;
        double minDist = 999999999;
        int indexGC = -1;
        
        try{
            // on cherche un noeud proche dans les index
            for (int i = 0; i <= 1; i++){
                if (index(i).size() > 0) {
                    tempNode = index(i).nearest(coord);
                    if (tempNode.distance(coord) < minDist) {
                        closestNode = tempNode;
                        minDist = tempNode.distance(coord);
                        indexGC = i;
                    }
                }
            }
        } catch(Exception e){
            e.printStackTrace();
        }
        
        result[0] = closestNode;
        result[1] = minDist;
        result[2] = indexGC;
        
        return result;
    }
    
    /*
    * Trouve le NoeudAGarder le plus proche des coordonnées indiquées
    * en distance à vol d'oiseau (Node.distance()) ou le crée éventuellement
    * si aucun noeud n'est trouvé à une distance inférieure à toleranceNoeud
    */
    private NoeudAGarder getNoeud(double[] coord, int iMax){
        Node n = null;
        try{
            // on cherche si un noeud existe déjà à cet emplacement dans les index jusqu'au "sien" (le remplissage se faisant dans cet ordre)
            for (int i = 0; i <= iMax ;i++){
                n = index(i).search(coord);
                if (n != null && n instanceof NoeudAGarder) return (NoeudAGarder) n;
            }
            try{
                // on cherche un noeud proche dans les index
                for (int i = 0; i <= iMax;i++){
                    if (index(i).size() > 0) n = index(i).nearest(coord);
                    else n = null;
                    if (n != null && n instanceof NoeudAGarder && n.distance(coord) < toleranceNoeud){
                        index(iMax).insert(coord, n); // plusieurs coordonnées peuvent bien pointer sur le même noeud, même parmi différents index
                        return (NoeudAGarder) n;
                    }
                }
                // si on n'a vraiment trouvé aucun noeud correspondant déjà existant, il est créé
                compteurNoeud++;
                n = new NoeudAGarder(compteurNoeud, coord);
                graphe.addVertex((NoeudAGarder) n);
                index(iMax).insert(coord, n);
                return (NoeudAGarder) n;
            } catch(KeyDuplicateException e){
                // si jamis il y avait un NoeudInterne au mauvais endroit, on l'ôte de l'index en question pour mettre un vrai Noeud
                //System.out.println("Utilisation de KeyDuplicateException dans getNoeud"); // ça arrive au moins qqs fois à chaque NRO, et parfois  souvent
                index(iMax).delete(coord);
                index(iMax).insert(coord, n);
                return (NoeudAGarder) n;
            }
        } catch(Exception e){
            e.printStackTrace();
        }
        System.out.println("Utilisation du return qui ne devrait pas l'être lors de la création d'un noeud !");
        return (NoeudAGarder) n;
    }

    public NoeudInterne getNoeudInterne(double[] coord, int i, NoeudAGarder n1, NoeudAGarder n2, Arete a, double distanceADroite){
        compteurNoeud++;
        NoeudInterne n = new NoeudInterne(compteurNoeud, coord, n1, n2, a, distanceADroite, i);
        try{
            if (index(i).search(coord) == null){
               index(i).insert(coord, n);
            }
        } catch(Exception e){
            e.printStackTrace();
        }
        return n;
    }
    
    private void addArete(NoeudAGarder n1, NoeudAGarder n2, int modePose, double longueur, long idProprio, List<double[]> intermediaires, String type, boolean voisin){
        try{
            double longueurPonderee = longueur*multiple(modePose, false);
            
            compteurArete++;
            Arete a = new Arete(compteurArete, modePose, idProprio, longueur);
            a.addIntermediaires(intermediaires, indice(type,voisin), n1, n2,this);                
            graphe.addEdge(n1, n2, a);
            graphe.setEdgeWeight(a, longueurPonderee);
            
        } catch(Exception e){
            e.printStackTrace();
        }
    }
    
    private void addArete(NoeudAGarder n1, NoeudAGarder n2, int modePose, double longueur, long idProprio, List<NoeudInterne> intermediaires){
    	addArete(n1, n2, modePose, longueur, idProprio, intermediaires, false);
    }
    
    private void addArete(NoeudAGarder n1, NoeudAGarder n2, int modePose, double longueur, long idProprio, List<NoeudInterne> intermediaires, boolean dejaUtilise){
        compteurArete++;
        Arete a = new Arete(compteurArete, modePose, idProprio, longueur);
        a.dejaUtilise = dejaUtilise;
        a.changeAndAddIntermediaires(intermediaires,n1,n2);
        graphe.addEdge(n1, n2, a);
        try{
            graphe.setEdgeWeight(a, longueur*multiple(modePose, dejaUtilise));
        } catch(Exception e){
            e.printStackTrace();
        }
    }
    
    /*
    * Méthode addArete avec modePose = 12 (GC reconstruit par forceConnexite), idProprio = -1)
    * et une liste de NoeudInterne vide
    */
    private void construitAreteConnexite(NoeudAGarder n1, NoeudAGarder n2, double longueur){
        addArete(n1, n2, 12, longueur*coefVdO, -1, new ArrayList<NoeudInterne>());
    }
    
    /*
    * Méthode addArete avec modePose = 13 (GC reconstruit par addPC), idProprio = -1)
    * et une liste de NoeudInterne vide
    */
    private void construitAretePC(NoeudAGarder n1, NoeudAGarder n2, double longueur){
        addArete(n1, n2, 13, longueur*coefVdO, -1, new ArrayList<NoeudInterne>());
    }
    
    /*
    * Méthode addArete avec modePose = 14 (GC reconstruit par connecteRoutes), idProprio = -1)
    * et une liste de NoeudInterne vide
    */
    private void construitAreteConnecteRoute(NoeudAGarder n1, NoeudAGarder n2, double longueur){
        addArete(n1, n2, 14, longueur*coefVdO, -1, new ArrayList<NoeudInterne>());
    }
    
    /*
    * Méthode utilisée par la méthode forceConnexité pour construire le plus
    * petit pont possible entre deux îlots connexes du réseau
    * Le résultat est une liste contenant les deux NoeudAGarder à relier
    */
    private List<NoeudAGarder> trouverPont(Set<NoeudAGarder> s1, Set<NoeudAGarder> s2){
        NoeudAGarder n1 = null,n2 = null;
        List<NoeudAGarder> res = new ArrayList<>();
        // on fait en sorte d'indexer le grand ensemble de noeuds pour être en O(nlog(m)) si n < m
        Set<NoeudAGarder> petit;
        Set<NoeudAGarder> grand;
        if (s1.size() <= s2.size()){
            petit = s1;
            grand = s2;
        } else{
            petit = s2;
            grand = s1;
        }
        KDTree<NoeudAGarder> index = new KDTree<>(2);
        try{
            for (NoeudAGarder n : grand){
                if (index.search(n.coord) == null) index.insert(n.coord, n);
            }
            double distance, distMin = 10000000;
            for (NoeudAGarder n : petit){
                NoeudAGarder plusProche = index.nearest(n.coord);
                distance = n.distance(plusProche);
                if (distance < distMin){
                    distMin = distance;
                    n1 = n;
                    n2 = plusProche;
                }
            }
            res.add(n1);
            res.add(n2);
        } catch(Exception e){
            e.printStackTrace();
        }
        return res;
    }
 
    
    private double distanceDijkstra(NoeudAGarder noeud){
    	return noeud.meilleurChemin.distanceAuCentre;
    }
    
    private double distanceDijkstra(Node n){
        if (n instanceof NoeudAGarder){
            return distanceDijkstra((NoeudAGarder) n);
        } else{
            NoeudInterne ni = (NoeudInterne) n;
            try{
            double d0 = ni.distance*multiple(ni.a.modePose, false) + distanceDijkstra(ni.voisins[0]);
            double d1 = (graphe.getEdgeWeight(ni.a) - ni.distance)*multiple(ni.a.modePose, false) + distanceDijkstra(ni.voisins[1]);
            if (d0 <= d1) return d0;
            else return d1;
            } catch(Exception e){
                e.printStackTrace();
            }
        }
        return 0;
    }
    
    /*
    * Calcule la distance au NRO (Centre) d'un point de coordonnées coord
    * en empruntant le GC ou bien la route suivant type
    * La méthode trouve le Node n1 le plus proche dans l'index (KDTree) référençant
    * les arêtes choises et calcule la distance (plus court chemin) entre coord
    * et le centre comme :
    * - la longueur du plus court chemin entre n1 et le NRO
    * - plus la distance à vol d'oiseau entre coord et n1, en GC reconstruit
    * 
    * Si la ZANRO possède une partie dans un département voisin, la distance est
    * également calculé utilisant les en empruntant les arêtes y compris celles situées dans
    * le département voisin ; cette distance est retenue si elle est plus courte
    * que la première.
    */
    private Object[] distanceAuCentre(double[] coord, String type){
        Object[] result = new Object[3];
        try{
            Node n1 = index(indice(type,false)).nearest(coord);
            double d1 = n1.distance(coord)*multiple(11, false)+ distanceDijkstra(n1);
            result[0] = d1;
            result[1] = n1;
            result[2] = indice(type,false);
            if (index(indice(type,true)).size() > 0){
                Node n2 = index(indice(type,true)).nearest(coord);
                double d2 = n2.distance(coord)*multiple(11, false)+ distanceDijkstra(n2);
                if (Parametres.arrondir(d2,3) < Parametres.arrondir(d1,3)){
                    result[0] = d2;
                    result[1] = n2;
                    result[2] = indice(type,true);
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        return result;
    }
    
    /**
     * fonction pour modifier le graphe quand un noeud interne devient finalement à garder
     * 
     * @param n NoeudInterne à convertir en NoeudAGarder
     * @param i (int) indice (max) de l'index dans lequel le NoeudAGarder est inséré
     * @param calculeDistanceAuCentre (booléen) vrai pour que la distance au centre
     * soit calculée (nécessite que le réseau soit complet), faux sinon
     * @return le nouveau NoeudAGarder
     */
    private NoeudAGarder keep(NoeudInterne n, int i, boolean calculeDistanceAuCentre){
        NoeudAGarder nouveau = getNoeud(n.coord, i);
        
        graphe.removeEdge(n.a);
        
        Iterator<NoeudInterne> iterator = n.a.intermediaires.iterator();
        Node precedent;
        NoeudInterne suivant = iterator.next(); // au moins 1 élément, n
        // calcul de la longueur entre n1 (n.voisin[0]) et nouveau
        // et récupération de la liste des intermédiaires entre n1 et nouveau
        double longueur = n.voisins[0].distance(suivant);
        List<NoeudInterne> intermediaires1 = new ArrayList<>();
        while (!suivant.equals(n)){
            intermediaires1.add(suivant);
            precedent = suivant;
            suivant = iterator.next();
            longueur += precedent.distance(suivant);
        }
        // calcul de la longueur entre n2 (n.voisin[1]) et nouveau
        // et récupération de la liste des intermédiaires entre n2 et nouveau
        double longueur2 = 0;
        List<NoeudInterne> intermediaires2 = new ArrayList<>();
        precedent = n;
        while (iterator.hasNext()){
            suivant = iterator.next();
            longueur2 += precedent.distance(suivant);
            intermediaires2.add(suivant);
            precedent = suivant;
        }
        longueur2 += precedent.distance(n.voisins[1]);
        var longueurtot = longueur + longueur2;
        longueur = Parametres.arrondir(n.a.longueur * longueur / longueurtot, 1);
        longueur2 =  Parametres.arrondir(n.a.longueur - longueur, 1);
        
        // Ajout de l'arête entre n1 et nouveau
        addArete(n.voisins[0],nouveau,n.a.modePose,longueur,n.a.idProprio,intermediaires1, n.a.dejaUtilise);
        
        // Ajout de l'arête entre nouveau et n2
        addArete(nouveau,n.voisins[1],n.a.modePose, longueur2, n.a.idProprio, intermediaires2, n.a.dejaUtilise);
        
        // Calcul éventuel du plus court chemin vers le NRO
        if(calculeDistanceAuCentre) {
        	this.dijkstraReseau.recompute(n.voisins[0], n.voisins[1], nouveau); // pour s'assurer que le pathToCentre existe (il faut bien ajouter les arêtes d'abord))
        }
        
        return nouveau;
    }
    
    /**
     * Méthode indiquant le poids linéique des arêtes en fonction de leur mode de pose
     * et, depuis la v1.2, de leur caractère utilisé pour un plus court chemin
     * auparavant (si une arête doit être construite de toute façon pour relier un
     * PC, il n'est plus nécessaire de la construire pour relier les PC suivants)
     * 
     * @param modePose (int) code du mode de pose de l'arête, correspondant au
     *      mode de pose dans le fichier de GC d'Orange ou au mode attribué par
     *      le modèle pour la route et les arêtes reconstruites
     * @param dejaUtilise (bool) à True pour repondérer une arête utilisée pour 
     *      relier un PC
     * @return  (double) poids linéique de l'arête
     * @throws Exception 
     */
    private double multiple (int modePose, boolean dejaUtilise) {
        if (modePose == -1){
            throw new RuntimeException("Le mode de pose n'a pas été renseigné au préalable !");
        } else {
            double multiplicateur = facteurConduite; // modePose = 3,5,6,7,8,9,10
            switch(modePose){
                case 0:
                case 1:
                case 2:
                    multiplicateur = facteurAerien;
                    break;
                case 4:
                case 11:
                case 12:
                case 13:
		case 14:
                    if (! dejaUtilise) multiplicateur = facteurPleineTerre;
                    else multiplicateur = facteurConduite;
                    break;
            }
            return multiplicateur;
        }
    }
    
    /**
     * Réévalue la longueur d'une arete car on l'utilise
     * @param a
     */
    public void utiliseArete(Arete a) {
		a.dejaUtilise = true;
		var longueurPondere = Parametres.arrondir(a.getLongueur()*multiple(a.getModePose(), true), 1);
		if (longueurPondere > this.graphe.getEdgeWeight(a)) {
			throw new RuntimeException("ca pue");
		}
		this.graphe.setEdgeWeight(a, longueurPondere);
    }
    
    /**
     * Fonction écrivant un shapefile représentant le graphe du réseau
     * 
     * @param crs (CoordinateReferenceSystem) Système de coordonnées de référence pour l'écriture du shapefile
     * @param dossier (File) Répertoire où écrire le shapefile
     */
    public void printShapefiles(CoordinateReferenceSystem crs, File dossier){
        System.out.println("Ecriture du shapefile réseau pour " + this.id);
        GeometryFactory gf = JTSFactoryFinder.getGeometryFactory(new Hints(Hints.JTS_SRID, Integer.parseInt(crs.getIdentifiers().iterator().next().getCode())));
        
        // Tracé des Noeuds
        SimpleFeatureType typeShpNaG = NoeudAGarder.getFeatureType(crs);
        SimpleFeatureBuilder featureBuilderNaG = new SimpleFeatureBuilder(typeShpNaG);
        List<SimpleFeature> featuresNaG = new ArrayList<>();
        
        for(NoeudAGarder n : graphe.vertexSet()) {
            featuresNaG.add(n.getFeature(this.id, gf, featureBuilderNaG));
        }
        Shapefiles.printShapefile(dossier+"/" + id + "_noeuds", typeShpNaG, featuresNaG);
        
        // Tracé des arêtes
        SimpleFeatureType typeShpAretes = Arete.getFeatureType(crs);
        SimpleFeatureBuilder featureBuilderAretes = new SimpleFeatureBuilder(typeShpAretes);
        List<SimpleFeature> featuresAretes = new ArrayList<>();
        
        for(Arete a : graphe.edgeSet()) {
            Coordinate[] coord = a.getPoints(graphe.getEdgeSource(a), graphe.getEdgeTarget(a));
            featuresAretes.add(a.getFeature(coord, this.id, graphe.getEdgeWeight(a), gf, featureBuilderAretes));
        }
        Shapefiles.printShapefile(dossier+"/" + id + "_aretes", typeShpAretes, featuresAretes);
        
        System.out.println("Ecriture du shapefile réseau pour " + this.id + " terminée.");
    }
}
