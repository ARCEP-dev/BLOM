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

import de.biomedical_imaging.edu.wlu.cs.levy.CG.*;
import java.io.*;
import java.util.*;
import org.jgrapht.Graph;
import org.jgrapht.graph.*;

public class ReseauCuivre {
    
    private final String cheminReseau;
    private final String dpt;
    private boolean lireSR;
    private final int nbLignesMinNRO;
    private final double distMaxNRONRA;
    
    private Map<String,Map<String,NRA>> listeNRA; // plusieurs clés peuvent pointer vers le même NRA (gestion des NRA manquants)
    private Map<String,NRA> listeNRAinitiaux;
    private KDTree<NRA> indexNRA;
   
    private Map<String,Map<String,SR>> listeSR;
    private Map<String, SR> listeSRinitiaux;
    private KDTree<SR> indexSR;
    
    private ArrayList<PC> listePC;
    private Map<String,List<NRA>> listeNRO;
    
    private final Comparateur comparateurNRA = new Comparateur();

    // objets contenant le reseau modelise
    public ReseauCuivre(String cheminReseau, String dpt, int nbLignesMinNRO, double distMaxNRONRA){
        this.cheminReseau = cheminReseau;
        this.dpt = dpt;
        this.nbLignesMinNRO = nbLignesMinNRO;
        this.distMaxNRONRA = distMaxNRONRA;
        this.lireSR = false;
    }

    public void loadNRA(String dirNRA){
        System.out.println("Lecture des NRA du département "+dpt);
        listeNRA = new HashMap<>();
        listeNRAinitiaux = new HashMap<>();
        indexNRA = new KDTree<>(2);
        File dir = new File(dirNRA);
        String ligne, codeNRA = "";
        String[] donneesLigne;
        double[] coord = new double[2];
        NRA nra;
        int pos_codenra = 0;
        int pos_coordx = 1;
        int pos_coordy = 2;
        try{               
            BufferedReader ptRes = new BufferedReader(new FileReader(dir+"/"+dir.getName()+"_"+ dpt + ".csv"));
            ptRes.readLine();
            while ((ligne = ptRes.readLine()) != null) { 
                donneesLigne = ligne.split(";");
                codeNRA = donneesLigne[pos_codenra].replace("\"", ""); 
                if (codeNRA.substring(5,7).equals("E0"))
                    codeNRA = codeNRA.substring(0, 5) + "EOO";
                coord[0] = Double.parseDouble(donneesLigne[pos_coordx].replace("\"", "").replace(",", ".")); 
                coord[1] = Double.parseDouble(donneesLigne[pos_coordy].replace("\"", "").replace(",", ".")); 
                nra = new NRA(codeNRA, coord[0], coord[1], false);
                listeNRAinitiaux.put(codeNRA, nra);
                try {indexNRA.insert(coord, nra);}
                catch (KeyDuplicateException e){System.out.println(e+" - type : NRA");}                
            }
            ptRes.close();
        }catch(Exception e){
            System.out.println("Exception lors de la lecture des NRA");
            System.out.println("Dpt "+dpt+"- NRA "+codeNRA);
            e.printStackTrace();
        }
        System.out.println("Nombre de NRA du réseau cuivre départemental (en lecture) : "+listeNRAinitiaux.values().size());
    }

    public void loadSR(String dirSR){
        System.out.println("Lecture des SR du département "+dpt);
        this.lireSR = true;
        listeSR = new HashMap<>();
        listeSRinitiaux = new HashMap<>();
        indexSR = new KDTree<>(2);
        File dir = new File(dirSR);
        String ligne, codeNRA = "", codeSR = "";
        String[] donneesLigne;
        double[] coord = new double[2];
        SR sr;
        int pos_codesr = 0;
        int pos_codenra = 1;
        int pos_coordx = 2;
        int pos_coordy = 3;
        try{ 
            BufferedReader ptRes = new BufferedReader(new FileReader(dir+"/"+dir.getName()+"_"+ dpt + ".csv"));
            ptRes.readLine();
            
            while ((ligne = ptRes.readLine()) != null) { 
                donneesLigne = ligne.split(";");
                codeSR = donneesLigne[pos_codesr].replace("\"","");
                codeNRA = donneesLigne[pos_codenra].replace("\"", "");
                if (codeNRA.substring(5,7).equals("E0"))
                    codeNRA = codeNRA.substring(0, 5) + "EOO";
                coord[0] = Double.parseDouble(donneesLigne[pos_coordx].replace("\"", "").replace(",", ".")); 
                coord[1] = Double.parseDouble(donneesLigne[pos_coordy].replace("\"", "").replace(",", "."));
                sr = new SR(codeSR, coord[0], coord[1], codeNRA);
                try{
                    indexSR.insert(coord, sr);
                } catch (KeyDuplicateException e){
                    sr = indexSR.search(coord);
                    System.out.println(e+" - type : SR");
                }
                listeSRinitiaux.put(codeSR, sr);              
            }
            ptRes.close();
        }catch(Exception e){
            System.out.println("Exception lors de la lecture des SR");
            System.out.println("Dpt "+dpt+"- NRA "+codeNRA+"- SR "+codeSR);
            e.printStackTrace();
        }
        System.out.println("Nombre de SR du réseau cuivre départemental (en lecture) : "+listeSRinitiaux.values().size());
    }

    public void loadPC(String dirPC, boolean analyse, PrintWriter sortie, boolean lireSR){
        System.out.println("Lecture des PC du département "+dpt);
        listePC = new ArrayList<>();
        String ligne, codePCSR = "", codeSR = "", zone, codeNRA  = "";
        String[] donneesLigne;
        int nbAcces;
        double[] coord = new double[2];
        NRA nra;
        PC pc;
        PointReseau pt;
        int pos_codepcsr = 0;
        int pos_codenra = 1;
        int pos_codesr = 2;
        int pos_coordx_pc = 4;
        int pos_coordy_pc = 5;
        int pos_nbacces = 7;
        int pos_zone = 8;
        // gestion des galeries visitables
        boolean lireCommunes = dpt.equals("13")||dpt.equals("69");
        List<String> codeCommunes = communes(dpt);
        boolean paris = dpt.equals("75");
        
        File dir = new File(dirPC);

        try{               
            BufferedReader ptRes = new BufferedReader(new FileReader(dir+"/"+dir.getName()+"_"+ dpt + ".csv"));
            ptRes.readLine();
            while ((ligne = ptRes.readLine()) != null) {
                donneesLigne = ligne.split(";");
                codePCSR = donneesLigne[pos_codepcsr].replace("\"", ""); // la concaténation de la clé PC et de la clé SR contenues dans la base des lignes cuivre utilisée permet d'identifier les PC (la clé PC n'est pas suffisante)
                codeNRA = donneesLigne[pos_codenra].replace("\"", ""); 
                codeSR = donneesLigne[pos_codesr].replace("\"",""); 
                if (codeNRA.substring(5,7).equals("E0"))
                    codeNRA = codeNRA.substring(0, 5) + "EOO";
                coord[0] = Double.parseDouble(donneesLigne[pos_coordx_pc].replace("\"", "").replace(",", ".")); 
                coord[1] = Double.parseDouble(donneesLigne[pos_coordy_pc].replace("\"", "").replace(",", "."));
                nbAcces = Integer.parseInt(donneesLigne[pos_nbacces]);
                zone = donneesLigne[pos_zone];
                if (nbAcces > 0){ // on exclut par précaution normalement inutile les PC qui n'ont pas au moins 1 accès
                    nra = getNRA(codeNRA, zone, coord); // on rattache au NRA associé au code NRA lié au PC par défaut, sinon on rattache au NRA le plus proche si on ne retrouve pas le code NRA et en dernier recours on crée un NRA avec les coordonnées du PC si aucun NRA pertinent n'est trouvé.
                    if (lireSR){ // si on utilise les SR on rattache au SR
                        pt = getSR(codeSR, zone, nra);     
                    } else{ // sinon on rattache au NRA
                        pt = nra;
                    }
                    pc = new PC(codePCSR,  coord[0], coord[1], pt, codeNRA, nbAcces, zone, paris||(lireCommunes&&codeCommunes.contains(donneesLigne[8])));
                    listePC.add(pc);
                    if (!lireSR) nra.addFils(pc, indexNRA);
                    nra.ajoutPC(pc);
                }

            }
            ptRes.close();
            System.out.println("Nombre de NRA du réseau cuivre départemental (en lecture) : "+listeNRAinitiaux.values().size());
            System.out.println("Nombre par zone aprés attribution :");
            for (String s : listeNRA.keySet()){
                System.out.println(" - "+listeNRA.get(s).values().size()+" en zone "+s);
            }
            
            if (lireSR){
                System.out.println("Nombre de SR du réseau cuivre départemental (en lecture) : "+listeSRinitiaux.values().size());
                System.out.println("Nombre par zone aprés attribution :");
                for (String s : listeSR.keySet()){
                    System.out.println(" - "+listeSR.get(s).values().size()+" en zone "+s);
                }
            }
            System.out.println("Nombre de PC du réseau cuivre départemental : "+listePC.size());

            // analyse éventuelle des "nouveaux NRA"
            if (analyse){
                int compteurNRA = 0;
                int compteurFils = 0;
                int compteurLignes = 0;
                for (Map<String,NRA> liste : this.listeNRA.values()){
                    for (NRA nra1 : liste.values()){
                        if (nra1.nouveau){
                            compteurNRA++;
                            compteurFils += nra1.getFils().size();
                            compteurLignes += nra1.podi();
                        }
                    }
                }
                System.out.print(compteurNRA+" nouveaux NRA ont été créés, totalisant "+compteurFils);
                if (lireSR) System.out.print(" SR");
                else System.out.print(" PC");
                System.out.println(" et "+compteurLignes+" lignes.");
                sortie.println(dpt+";"+compteurNRA+";"+compteurFils+";"+compteurLignes);
                
            }
            
        } catch(Exception e){
            System.out.println("Exception lors de la lecture des PC");
            System.out.println("Dpt "+dpt+"- NRA "+codeNRA+"- SR "+codeSR+"- PC "+codePCSR);
            e.printStackTrace();
        }
    }
    
    private List<String> communes(String dpt){
        List<String> liste = new ArrayList<>();
        switch(dpt){
            case "13":
                liste.add("13055");
                liste.add("13201");
                liste.add("13202");
                liste.add("13203");
                liste.add("13204");
                liste.add("13205");
                liste.add("13206");
                liste.add("13207");
                liste.add("13208");
                liste.add("13209");
                liste.add("13210");
                liste.add("13211");
                liste.add("13212");
                liste.add("13213");
                liste.add("13214");
                liste.add("13215");
                liste.add("13216");
                break;
            case "69":
                liste.add("69000");
                liste.add("69123");
                liste.add("69381");
                liste.add("69382");
                liste.add("69383");
                liste.add("69384");
                liste.add("69385");
                liste.add("69386");
                liste.add("69387");
                liste.add("69388");
                liste.add("69389");
                break;
            default:
                liste = null;
        }
        return liste;
    }
    
    // gestion des NRA 
    private NRA getNRA(String codeNRA, String z, double[] coord){
        String zone = z.replace("_HD", "").replace("_BD", "");
        if (!listeNRA.containsKey(zone)){
            listeNRA.put(zone, new HashMap<String,NRA>());
        }
        NRA nra = listeNRA.get(zone).get(codeNRA); // on complète la liste des NRA de façon incrémentale par rapport à la liste des NRA en se basant sur le fichier des lignes cuivre
        if (nra == null){
            nra = listeNRAinitiaux.get(codeNRA); // fonctionnement nominal de la fonction : on recherche le code NRA dans la liste des NRA connus
            if (nra != null){
                nra = new NRA(nra, zone);
                listeNRA.get(zone).put(codeNRA, nra);
            } else{ // le NRA n'a pas ete retrouve dans la liste des NRA initiale, on met à jour la liste utilisée par le modèle.
                System.out.println("Pas de NRA correspondant au code : "+codeNRA);
                try{
                    nra = indexNRA.nearest(coord); // on recherche le NRA plus proche 
                    if (nra.distance(coord) > 10000){ // s'il est vraiment trop loin on créée un nouveau NRA qu'on ajoute à la liste des NRA (et au 2D-Tree avec la position du PC)
                        System.out.println("On crée un nouveau NRA");
                        nra = new NRA(codeNRA, 0, 0, true, zone);                    
                        indexNRA.insert(coord, nra);
                        listeNRA.get(zone).put(codeNRA, nra);
                    } else{ // s'il est assez près on sélectionne le NRA le plus proche qu'on ajoute à la liste des NRA
                        if (listeNRA.get(zone).containsKey(nra.identifiant)){
                            nra = listeNRA.get(zone).get(nra.identifiant);
                        } else{
                            nra = new NRA(nra,zone);
                            listeNRA.get(zone).put(codeNRA, nra);
                        }
                        nra.addNRA(codeNRA);
                    }
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
        return nra;
    }
    
    // gestion des SR  (il est plus commode de rattacher directement au NRA)
    private SR getSR(String codeSR, String z, NRA nra){
        String zone = z.replace("_HD", "").replace("_BD", "");
        if (!listeSR.containsKey(zone))
            listeSR.put(zone, new HashMap<String, SR>());
        SR sr = listeSR.get(zone).get(codeSR);  
        if (sr == null) {
            sr = listeSRinitiaux.get(codeSR);
            if (sr!=null){
                sr = createSR(sr, zone);
            } else{ 
                // gestion des PC rattachés directement au NRA, codage pas toujours clair dans les fichiers
                double[] coord = {nra.x, nra.y};
                try{
                    sr = indexSR.search(coord);
                    if (sr == null){
                        sr = new SR(codeSR, coord[0], coord[1], nra.identifiant);   
                        try{
                            indexSR.insert(coord, sr);
                        } catch (KeyDuplicateException e){
                            System.out.println("WARNING : on ne devrait pas avoir d'exception ici !");
                        }
                        sr = createSR(sr, zone);
                    } else{
                        if (listeSR.get(zone).containsKey(sr.identifiant)){
                            sr = listeSR.get(zone).get(sr.identifiant);
                        } else
                            sr = createSR(sr, zone);
                    }
                } catch (KeySizeException e){
                    e.printStackTrace();
                }
            }  
        }
        return sr;
    }
    
    private SR createSR(SR sr, String zone){
        SR sousrep = new SR(sr, zone);
        double[] coord = {sr.x, sr.y};
        NRA nra = getNRA(sousrep.codeNRA, sousrep.zone, coord);
        nra.addFils(sousrep, indexNRA);
        sousrep.rattachePere(nra);
        listeSR.get(zone).put(sr.identifiant, sr);
        return sousrep;
    }

    public void testPC(String dossier){
        try{
            System.out.println("Nombre de NRA dans le fichier d'entrée : "+listeNRAinitiaux.size());
            if (lireSR)
                System.out.println("Nombre de SR dans le fichier d'entrée : "+listeSRinitiaux.size());
            System.out.println("Nombre total de PC : "+listePC.size());
            System.out.println("Impression liste PC");
            File dir = new File(dossier);
            dir.mkdirs();
            PrintWriter csv = new PrintWriter(dir+"/"+dir.getName()+"_"+dpt+"_testPC.csv");
            csv.println("Code PC;Code NRA initial;Code NRA final;Zone;Nb de lignes");
            for (PC pc : listePC){
                csv.print(pc.identifiant+";"+pc.nra+";");
                if (lireSR) csv.print(pc.pere.pere.identifiant);
                else csv.print(pc.pere.identifiant);
                csv.println(";"+pc.zone+";"+pc.lignes);
            }   
            csv.close();
        } catch(FileNotFoundException e){
        e.printStackTrace();
        }
    }

    public List<String> etat(){
        List<String> res = new ArrayList<>();
        for (String zone : this.listeNRA.keySet()){
            for (NRA nra : this.listeNRA.get(zone).values()){
                String s = dpt+";"+zone+";"+nra.identifiant;
                if (lireSR) s+=";"+nra.getFils().size();
                res.add(s+";"+nra.getPC().size()+";"+nra.podi());
            }
        }
        return res;
    }

    public void checkPC(double distanceLimite){ // fonction de filtrage des PC trop éloignés de leurs NRA, utilisée en prétraitement du regroupement des NRA
        for (String zone : this.listeNRA.keySet()){
            for(NRA nra : this.listeNRA.get(zone).values()){
                nra.checkPC(distanceLimite);
            }
        }
    }
    
    public void regrouperNRACollecte(String sortie, String dossierCollecte){
        
        // regroupement des NRA zone par zone (ZTD/AMII/RIP)
        this.listeNRO = new HashMap<>();
        for (String zone : this.listeNRA.keySet()){
            
            System.out.println("Début du regroupement pour la zone :"+zone);
            SimpleWeightedGraph<NRA, DefaultWeightedEdge> grapheCollecte = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
            KDTree<NRA> indexCollecte = new KDTree<>(2);
            
            // ajout des NRA au graphe
            this.addNRA(grapheCollecte, indexCollecte, zone);
            
            // ajout des liens de collecte connus au graphe
            System.out.println("chargement du réseau de collecte");
            this.readFichierCollecte(grapheCollecte, zone, dossierCollecte);
            
            // version post CP1 : suppression de l'ajout initial de liens pour les NRA isolés compte tenu du fait que :
            // - on va chercher des liens à vol d'oiseau pour les NRA isolés à l'issue des regroupements des NRA à un ou plusieurs voisins (comme en CP1)
            // - on ne vérifie plus seulement les distances de proche en proche donc l'ajout de liens  peut conduire à empêcher un regroupement 
            // ajout de liens pour les NRA isolés
            // System.out.println("Ajout de liens pour les NRA isolés");
            //this.ajoutNRAIsoles(grapheCollecte, indexCollecte);

            // Tous les NRA du réseau modélisé ont été ajoutés au graphe de collecte
            for (NRA nra : grapheCollecte.vertexSet()){
                System.out.println("NRA "+nra.identifiant+" : "+nra.podi()+" lignes");
            }
            
            // Affichage de l'intégralité des liens des NRA du graphe de collecte
            System.out.println("Impression du graphe");
            for (NRA nra1 : grapheCollecte.vertexSet()){
                System.out.print("NRA "+nra1.identifiant+", voisins :");
                for (DefaultWeightedEdge arete : grapheCollecte.edgesOf(nra1)){
                    NRA voisin = getVoisin(grapheCollecte, nra1, arete);
                    System.out.print(" - "+voisin.identifiant);
                }
                System.out.println();
            }
            
            // idée de l'algorithme :
            // but : regrouper tous les NRA en-dessous du seuil nbLignesMinNRO (a priori 1000 lignes)
            // - on aimerait avoir un arbre pour regrouper les NRA facilement mais le graphe créé n'a aucune raison d'être connexe et contient des cycles
            // - on va donc regrouper d'abord ceux qui n'ont qu'un voisin (les NRA dits "faciles")
            // - puis ceux qui ont plusieurs voisins (les NRA dits "deuxiemes"), tout en revenant en arriére dés qu'on obtient des NRA à 1 voisin par ce processus
            // - enfin, on regroupe ceux qui, suite aux regroupements, n'en ont plus mais sont toujours trop petits (les NRA dits "isolés")
            // - à chaque fois, on supprime le NRA regroupé du graphe et de l'index

            // initialisation des files de priorité pour le regroupement
            PriorityQueue<NRA> faciles = new PriorityQueue<>(10, comparateurNRA);
            PriorityQueue<NRA> deuxiemes = new PriorityQueue<>(10, comparateurNRA);
            PriorityQueue<NRA> isoles = new PriorityQueue<>(10, comparateurNRA);
           
            for (NRA nra : grapheCollecte.vertexSet()){
                if (nra.podi() < nbLignesMinNRO){
                    switch(grapheCollecte.edgesOf(nra).size()){
                        case 0: // ce cas n'est pas censé arriver au départ
                            isoles.add(nra);
                            System.out.println("Regroupement collecte : NRA trés éloigné de tous les autres");
                            System.out.println("Identifiant NRA : "+nra.identifiant);
                            break;
                        case 1:
                            faciles.add(nra);
                            break;
                        default:
                            deuxiemes.add(nra);
                            break;
                    }
                }
            }
            
	    // affichage des NRA par file de priorité
            System.out.println("Nombre de NRA à un voisin : "+faciles.size());

            System.out.println("Nombre de NRA à plusieurs voisins : "+deuxiemes.size());

            System.out.println("Nombre de NRA isolés : "+isoles.size());
            
            this.regroupeMonoVoisin(grapheCollecte, indexCollecte, faciles, deuxiemes, isoles); // on regroupe dynamiquement les faciles
            // note : malgré l'appel à regroupeMonoVoisin dans la fonction suivante on doit l'appeler une premiére fois de maniére isolée au cas où la file "deuxiémes" serait vide à l'origine
           
            // à ce moment il n'y a plus de nra dont le nombre de lignes est inférieur au seuil fixé par l'utilisateur et avec un unique voisin

            this.regroupeMultiVoisins(grapheCollecte, indexCollecte, faciles, deuxiemes, isoles);
            // cette fonction commence par appeler regroupeMonoVoisin lors de son exécution

            // désormais les seuls NRA dont le nombre de lignes est inférieur au seuil fixé par l'utilisateur n'ont pas (plus) de voisins
            this.regroupeSansVoisin(grapheCollecte, indexCollecte, isoles);

            // tous les "proto-NRO" dans le graphe à ce stade sont désormais considérés comme les NRO retenus et sont ajoutés dans les listeNRO correspondantes
            this.listeNRO.put(zone, new ArrayList<NRA>());
            for (NRA nra : grapheCollecte.vertexSet()){
                nra.nro = nra;
                this.listeNRO.get(zone).add(nra);
                System.out.print("NRA déclaré NRO : "+nra.identifiant+" ; NRA correspondants :");
                for (NRA nraReg : nra.listeNRAduNRO){
                    System.out.print(" - "+nraReg.identifiant);
                }
                System.out.println();
            }
           

            System.out.println("Nombre de NRO en zone "+zone+" : "+this.listeNRO.get(zone).size());
        }

        
        System.out.println("Verification inter zone");
        this.verificationInterZone(); // ajustement complementaire pour les NRO qui ne respectent pas le seuil de lignes
                                      // s'il on trouve un NRO avec le même code dans une autre zone qui a un nombre de lignes supérieur alors on rattache les lignes du NRO ne respectant pas le seuil à ce NRO
        
        for (String zone : this.listeNRO.keySet()){
            System.out.println("Nombre de NRO en zone "+zone+" : "+this.listeNRO.get(zone).size());
            this.store(sortie, false, zone);
        }
    }
    
    private void addNRA(SimpleWeightedGraph<NRA, DefaultWeightedEdge> collecte, KDTree<NRA> indexCollecte, String zone){
        Iterator<NRA> iterator = this.listeNRA.get(zone).values().iterator();
        NRA nra;
        Map<String, NRA> complementListeNRA = new HashMap<>();
        while (iterator.hasNext()){
            nra = iterator.next();
            double[] coord = {nra.x, nra.y};
            try{
                indexCollecte.insert(coord, nra);
                collecte.addVertex(nra);
            } catch(KeyDuplicateException e){
                System.out.println("Probléme avec le NRA "+nra.identifiant+" : deux NRA au méme endroit !");
                System.out.println("On regroupe directement dans ce cas");
                try{
                    NRA nro = indexCollecte.search(coord);
                    nro.regrouper(nra, 0); // on considere que les deux NRA sont situés au meme endroit donc on intialise la distance maximum de regroupement du NRA regroupeur à 0
                    iterator.remove();
                    complementListeNRA.put(nra.identifiant, nro);
                }   catch(KeySizeException e1){
                    e1.printStackTrace();
                }
            }catch(KeySizeException e){
                e.printStackTrace();
            }
        }
        this.listeNRA.get(zone).putAll(complementListeNRA);
    }
    
    private void readFichierCollecte(SimpleWeightedGraph<NRA, DefaultWeightedEdge> collecte, String zone, String dossierCollecte){
        try{
            BufferedReader fichierLiens = new BufferedReader(new FileReader(cheminReseau+dossierCollecte+"/"+dossierCollecte+"_"+dpt+".csv"));
            fichierLiens.readLine();
            String line;
            String[] fields;
            Map<String, NRA> listeNRAZone = this.listeNRA.get(zone);
            int pos_distancelien = 0;
            int pos_codenra1 = 1;
            int pos_codenra2 = 2;
            while ((line = fichierLiens.readLine())!=null){
                fields = line.split(";");
                String codeNRA1 = fields[pos_codenra1], codeNRA2 = fields[pos_codenra2];
                if (codeNRA1.endsWith("E00"))
                    codeNRA1 = codeNRA1.substring(0,5) + "EOO";
                if (codeNRA2.endsWith("E00"))
                    codeNRA2 = codeNRA2.substring(0,5) + "EOO";
                NRA nra1 = listeNRAZone.get(codeNRA1), nra2 = listeNRAZone.get(codeNRA2);
                double distance = Parametres.arrondir(Double.parseDouble(fields[pos_distancelien]),1);
                if (nra1!=null && nra2!=null && !nra1.equals(nra2) && distance < distMaxNRONRA){ // on ajoute le lien au graphe de collecte s'il on trouve les deux NRA dans la zone en cours de traitement, que les NRA du lien sont bien differents et que la longueur du lien est inférieure à la longueur maximum fixee (sinon placement errone dans les files de priorité)
                    //double distance = Parametres.arrondir(Double.parseDouble(fields[pos_distancelien]),1);
                    collecte.setEdgeWeight(collecte.addEdge(nra1, nra2), distance); // creation d'une arete ponderee par la longueur du lien entre les deux NRA
                }

            }
            fichierLiens.close();
            
            PrintWriter test = new PrintWriter(cheminReseau+"TestCollecte/TestCollecte_"+dpt+".csv");
            test.println("NRA;Liens");
            for (NRA nra : collecte.vertexSet()){
                test.println(nra.identifiant+";"+collecte.degreeOf(nra));
            }
            test.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    @SuppressWarnings("unused")
	private void ajoutNRAIsoles(SimpleWeightedGraph<NRA, DefaultWeightedEdge> collecte, KDTree<NRA> indexCollecte){
        List<NRA> restants = new ArrayList<>();

        for (NRA nra : collecte.vertexSet()){ // sélection des NRA isolés
            if (nra.podi() < nbLignesMinNRO && collecte.degreeOf(nra) == 0)
                restants.add(nra);
        }
        
        Collections.sort(restants, comparateurNRA); // les nra sont triés par "podi" croissant
        int n = indexCollecte.size(); // utilisé hors cas nominal, explications ci dessous
        for (NRA nra : restants){
            double[] coord = {nra.x, nra.y};
            NRA voisin = null;
            int k = 2;
            while (voisin == null){
                try{ // on cherche le plus proche voisin dans le KDTree des NRA, hors le NRA en cours de traitement
                    voisin = indexCollecte.nearest(coord ,k).get(0);
                }catch(KeySizeException e){
                    System.out.println("Exception anormale");
                    e.printStackTrace();
                }
                if (collecte.containsEdge(nra, voisin) && k<n){ // la deuxiéme condition est utile lorsque tous les nra de indexCollecte sont des NRA "restants" : on autorise alors un lien de moins (nécessaire dans un cas en "étoile")
                    voisin = null;
                    k++;
                }
            }
            
            double distance = nra.distance(voisin);
            if (distance <= distMaxNRONRA){ // on ajoute une arete dans le graphe si le plus proche voisin n'est pas trop loin
                try{
                    collecte.setEdgeWeight(collecte.addEdge(nra, voisin), distance);
                } catch(IllegalArgumentException e){
                    System.out.println("Exception dans l'ajout de liens pour NRA isolés : "+e.getMessage());
                }
            }
        }        
    }
    
    private void regroupeMonoVoisin(SimpleWeightedGraph<NRA, DefaultWeightedEdge> collecte, KDTree<NRA> indexCollecte, PriorityQueue<NRA> faciles, PriorityQueue<NRA> deuxiemes, PriorityQueue<NRA> isoles){
        // l'utilisateur de cette fonction garantit que les NRA présents dans la file des NRA dits "faciles" ont un unique voisin, moins de nbLignesMinNRO lignes, et sont classés par ordre croissant du nb de lignes
        try{
            while (!faciles.isEmpty()){
                
                NRA nra = faciles.poll();
                DefaultWeightedEdge e = collecte.edgesOf(nra).iterator().next(); // un seul lien de collecte par hypothése pour ce nra
                
                // on regroupe avec le seul voisin
                NRA voisin = getVoisin(collecte, nra, e);
                boolean petitVoisin = voisin.podi() < nbLignesMinNRO; // utile + tard
                System.out.println("Voisin choisi : "+voisin.identifiant);
                double distancemaxNRAregroupes = nra.distancemaxNRAregroupes + collecte.getEdgeWeight(e); // distance maximum en aval pour le NRA voisin candidat pour le regroupement dans le cas où le regroupement aurait lieu
                System.out.println("Distance au NRA voisin : " + collecte.getEdgeWeight(e));
                System.out.println("Distance max de regroupement NRA courant : " + nra.distancemaxNRAregroupes);
                System.out.println("Distance maximum éventuelle : " + distancemaxNRAregroupes);

                // if ( collecte.getEdgeWeight(e) <= distMaxNRONRA){
                if (distancemaxNRAregroupes <= distMaxNRONRA) { // on effectue le regroupement si la distance maximum en aval est inferieure au seuil fixe par l'utilisateur
                    System.out.println("NRA à un voisin regroupé : "+nra.identifiant);
                    System.out.println("Nouvelle distance maximum de regroupement : " + distancemaxNRAregroupes);
                    voisin.regrouper(nra, distancemaxNRAregroupes); // on regroupe le NRA courant et on met à jour la distance maximum en aval du NRA regroupeur
                    // on supprime "nra" du graphe et de l'index
                    collecte.removeVertex(nra);
                    double[] coord = {nra.x, nra.y};
                    indexCollecte.delete(coord);
                } else{ // le seuil de distance ne permet pas d'effectuer le regroupement, ce NRA va rejoindre la file des isolés (regroupement avec des NRA assez proches à vol d'oiseau)
                    collecte.removeEdge(e);
                    isoles.add(nra); // le NRA rejoint la file des NRA isolés (il était à regrouper au vu de son PODI mais il n'a pas été regroupé avec son voisin)
                }
                
                // gestion des files de priorité
                if (petitVoisin){ // la mise a jour de la file de priorite du NRA "voisin" est n'est pas necessaire s'il depassait deja le seuil minimal de lignes
                    switch(collecte.edgesOf(voisin).size()){
                        case 0: // donc 1 juste avant
                           faciles.remove(voisin);
                           if (voisin.podi() < nbLignesMinNRO)
                               isoles.add(voisin);
                            break;
                        case 1: // donc 2 juste avant
                            deuxiemes.remove(voisin);
                            if(voisin.podi() < nbLignesMinNRO)
                                faciles.add(voisin);
                            break;
                        default: // au moins 3 juste avant
                            if (voisin.podi() >= nbLignesMinNRO)
                                deuxiemes.remove(voisin);
                            break;
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    
    private void regroupeMultiVoisins(SimpleWeightedGraph<NRA, DefaultWeightedEdge> collecte, KDTree<NRA> indexCollecte, PriorityQueue<NRA> faciles, PriorityQueue<NRA> deuxiemes, PriorityQueue<NRA> isoles){
        while (!deuxiemes.isEmpty()){

            // on traite les NRA dits "faciles" qui sont créés lors du traitement de NRA à plusieurs voisins (aucun lors du premier passage)
            regroupeMonoVoisin(collecte, indexCollecte, faciles, deuxiemes, isoles); 

            NRA nra = deuxiemes.poll();
            if (nra != null){ // utile dans le cas où l'action de regroupeMonoVoisin a vidé "deuxiemes"
                System.out.println("NRA "+nra.identifiant+" de "+nra.podi()+" lignes");
                Set<DefaultWeightedEdge> liens = collecte.edgesOf(nra); // au moins deux éléments dans cet ensemble
                System.out.println("Test : l'ensemble des arétes du nra a "+liens.size()+" éléments.");

                // sélection du "plus proche voisin"
                Iterator<DefaultWeightedEdge> iterator = liens.iterator();
                DefaultWeightedEdge e = iterator.next(); // le premier par défaut
                double distance = collecte.getEdgeWeight(e);
                while (iterator.hasNext()){
                    DefaultWeightedEdge candidat = iterator.next();
                    double d = collecte.getEdgeWeight(candidat);
                    if (d < distance){ // le candidat pour regroupeur est le NRA le plus "proche" du NRA à regrouper dans le graphe de collecte
                        distance = d;
                        e = candidat;
                    }
                }
                double distancemaxNRAregroupes = nra.distancemaxNRAregroupes + distance;
                System.out.println("Distance au NRA candidat : " + distance);
                System.out.println("Distance max de regroupement NRA courant: " + nra.distancemaxNRAregroupes);
                System.out.println("Distance maximum éventuelle : " + distancemaxNRAregroupes);
                if (distancemaxNRAregroupes <= distMaxNRONRA){ // on effectue le regroupement si la distance maximum en aval du NRA candidat est inferieure au seuil fixe par l'utilisateur
                    NRA voisin = getVoisin(collecte, nra, e);
                    boolean petitVoisin = voisin.podi() < nbLignesMinNRO; // utile pour la mise a jour des files de priorites
                    voisin.regrouper(nra, distancemaxNRAregroupes);
                    System.out.println("Voisin choisi : "+voisin.identifiant);
                    System.out.println("Nouvelle distance maximum de regroupement : " + distancemaxNRAregroupes);
                    // avant de supprimer nra du graphe, il faut transférer les autres voisins de nra "voisin"; attention au cas particulier des "triangles"
                    for (DefaultWeightedEdge lien : liens){
                        if (!lien.equals(e)){
                            NRA v = getVoisin(collecte, nra, lien);
                            if (collecte.containsEdge(voisin, v)){ // cas du triangle -> possibilité de rendre des NRA "faciles"
                                if (v.podi() < nbLignesMinNRO && collecte.edgesOf(v).size() == 2){ // on n'a pas encore supprimé "nra" du graphe 
                                    faciles.add(v);
                                    deuxiemes.remove(v); //on conservera seulement l'arete entre v et voisin (pas d'ajout d'arete v-voisin en passant par nra)
                                }
                                if (petitVoisin){ //mise a jour des files dans le cas particulier dit du "triangle"
                                    if (liens.size() == 2 && collecte.edgesOf(voisin).size() == 2){
                                        deuxiemes.remove(voisin);
                                        petitVoisin = false; // pour ne pas répéter l'opération plus bas si on l'a déja effectuée
                                        if (voisin.podi() < nbLignesMinNRO)
                                            faciles.add(voisin);
                                    }
                                }
                            } else // cas général hors cas du "triangle"
                                collecte.setEdgeWeight(collecte.addEdge(voisin, v), distance + collecte.getEdgeWeight(lien));
                        }
                    }


                    // supression de nra du graphe et de l'index
                    collecte.removeVertex(nra);
                    double[] coord = {nra.x, nra.y};
                    try{
                        indexCollecte.delete(coord);
                    } catch(Exception excep){
                        excep.printStackTrace();
                    }
                    // gestion des files
                    if (petitVoisin && voisin.podi() >= nbLignesMinNRO)
                        deuxiemes.remove(voisin);
                } else{
                    // pas de probleme de triangle car on supprime purement et simplement les liens, et les podi des voisins n'ont pas changé non plus
                    // par définition les voisins n'étaient ni isolés ni a un voisin puisque faciles est vide
                    isoles.add(nra); // le NRA rejoint la file des NRA isolés (il était à regrouper au vu de son PODI mais il n'a pas été regroupé avec son voisin le plus proche)
                    Iterator<DefaultWeightedEdge> iter = (new ArrayList<>(liens)).iterator();
                    DefaultWeightedEdge lien;
                    while(iter.hasNext()){
                        lien = iter.next();
                        NRA voisin = getVoisin(collecte, nra, lien);
                        collecte.removeEdge(lien);
                        if (voisin.podi() < nbLignesMinNRO && collecte.degreeOf(voisin) == 1){
                            faciles.add(voisin);
                            deuxiemes.remove(voisin);
                        }
                    }
                }
            }
        }
    }
    
    private void regroupeSansVoisin(SimpleWeightedGraph<NRA, DefaultWeightedEdge> collecte, KDTree<NRA> indexCollecte, PriorityQueue<NRA> isoles){
        try{
            while (!isoles.isEmpty()){
                
                NRA nra = isoles.poll();
                
                // on supprime du graphe et de l'index
                double[] coord = {nra.x, nra.y};
                indexCollecte.delete(coord);
                
                if (indexCollecte.size() > 0){
                    NRA voisin = indexCollecte.nearest(coord);
                    double distance = nra.distance(voisin);
                    double distancemaxNRAregroupes = nra.distancemaxNRAregroupes + distance;
                System.out.println("Distance au NRA voisin à vol d'oiseau : " + distance);
                System.out.println("Distance max de regroupement NRA courant : " + nra.distancemaxNRAregroupes);
                System.out.println("Distance maximum éventuelle : " + distancemaxNRAregroupes);
                    if (distancemaxNRAregroupes <= distMaxNRONRA){ // on effectue le regroupement si la distance maximum en aval est inferieure au seuil fixe par l'utilisateur
                        // on supprime du graphe  et on regroupe avec le plus proche géographiquement
                        collecte.removeVertex(nra);
                        boolean petitVoisin = voisin.podi() < nbLignesMinNRO; // utile + tard
                        voisin.regrouper(nra, distancemaxNRAregroupes);
                    System.out.println("Nouvelle distance maximum de regroupement : " + distancemaxNRAregroupes);
                        // gestion de la file de priorité "isolés"
                        if (petitVoisin){ // alors nécessairement faisait partie de "isolés"
                            isoles.remove(voisin);
                            if (voisin.podi() < nbLignesMinNRO)
                                isoles.add(voisin); // s'il n'a toujours pas dépassé le seuil on le remet dans la liste
                        }
                    }
                }
                // si non regroupé, reste dans le graphe et sera regroupé avec lui-méme a la fin
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    
    private NRA getVoisin(Graph<NRA,DefaultWeightedEdge> graph, NRA nra, DefaultWeightedEdge edge){
        // le graphe est non-orienté
        NRA voisin = graph.getEdgeSource(edge);
        if (voisin.equals(nra))
            voisin = graph.getEdgeTarget(edge);
        return voisin;
    }
    
    private void store(String sortie, boolean cuivre, String zone){
        try{
            // réimpression d'un fichier de sortie
            System.out.println("Impression dans le fichier de sortie");
            File dir = new File(sortie);
            dir.mkdirs();
            String dossier = dir.getName();
            
            if (cuivre){
                PrintWriter csv = new PrintWriter(dir+"/"+dossier+"_"+dpt+"_"+zone+".csv");
                csv.println("NRA;SR;PC;X;Y;Type;Lignes;Zone;Parcelle;Isole;Changement de NRA;Changement de SR");
                for (NRA nra : listeNRA.get(zone).values()){
                    csv.println(nra.identifiant+";;;"+nra.x+";"+nra.y+";RE;;;;;;");
                }
                for (PointReseau sr : listeSR.get(zone).values()){
                    csv.println(sr.pere.identifiant+";"+sr.identifiant+";;"+sr.x+";"+sr.y+";SR;;;;;"+sr.changementNRA+";");
                }
                for (PC pc : listePC){
                    csv.println(pc.nra+";"+pc.pere.identifiant+";"+pc.identifiant+";"+pc.x+";"+pc.y+";PC;"+pc.lignes+";"+pc.zone+";"+pc.parcelle+";;"+pc.changementNRA+";"+pc.changementSR);
                }
                csv.close();
            } else{
                
                dir = new File(dir+"/"+dossier+"_"+dpt+"/"+dossier+"_"+dpt+"_"+zone);
                dir.mkdirs();
                PrintWriter csv = new PrintWriter(dir.getPath()+"/"+dir.getName()+".csv");
                csv.println("NRO;NRA;Type;X;Y;distancemaxregroupement");
                for (NRA nro : listeNRO.get(zone)) {
                    for (NRA nra : nro.listeNRAduNRO) {
                        csv.print(nro.identifiant + ";" + nra.identifiant);
                        if (nra.equals(nro)) {
                            csv.print(";NRO;");
                        } else {
                            csv.print(";NRA;");
                        }
                        
                        csv.println(nra.x + ";" + nra.y + ";" + Math.round(nra.distancemaxNRAregroupes));
                        for (String codeNRA : nra.getNRAComplementaires()){
                            csv.println(nro.identifiant + ";" + codeNRA+";NRA;"+nra.x+";"+nra.y);
                        }
                    }
                    PrintWriter pcNRO = new PrintWriter(dir.getPath()+"/ListePC_"+nro.identifiant+".csv");
                    pcNRO.println("Identifiant;X;Y;Lignes;Zone;PM_int;"+nro.x+";"+nro.y);
                    for (PC pc : nro.getPC()){
                        pcNRO.print(pc.identifiant+";"+pc.x+";"+pc.y+";"+pc.lignes+";"+pc.zone+";");
                        if (pc.PM_int) pcNRO.println(1);
                        else pcNRO.println(0);
                    }
                    pcNRO.close();
                }
                csv.close();
            }
        } catch(FileNotFoundException e){
            e.printStackTrace();
        }
    }

    public List<String> getAnalyseNRO(){ // methode permettant la création du fichier de synthese d'analyse des NRO
        List<String> res = new ArrayList<>();
        for (String zone : listeNRO.keySet()){
            for (NRA nro : listeNRO.get(zone)){
                String analyse = zone+";"+nro.identifiant;
                int nbNRA = 0;
                int nbNRAComplementaires = 0;
                int nbSR = 0;
                for (NRA nra : nro.listeNRAduNRO){
                    nbNRA++;
                    nbNRAComplementaires += nra.getNRAComplementaires().size();
                    nbSR+= nra.getFils().size();
                }
                analyse+=";"+(nbNRA+nbNRAComplementaires);
            if (lireSR) analyse+=";"+nbSR;
            analyse+=";"+nro.getPC().size()+";"+nro.podi();
                res.add(analyse);
            }
        }
        return res;
    }
    
    private void verificationInterZone(){ // traitement complementaire après le traitement des trois files de priorité pour les proto-NRO dont le nombre de lignes est en dessous du seuil 
        for (String zone : this.listeNRO.keySet()){
            List<NRA> newListeNRO = new ArrayList<>(this.listeNRO.get(zone));
            ListIterator<NRA> iterator = newListeNRO.listIterator();
            NRA nro;
            while (iterator.hasNext()){
                nro = iterator.next();
                NRA nouveauNRO = null;
                if (nro.podi() < nbLignesMinNRO){
                    int podiMax = nro.podi();
                    for (String autreZone : this.listeNRA.keySet()){ // on cherche s'il existe un NRA portant le même code dans une zone différente
                        if (!autreZone.equals(zone) && listeNRA.get(autreZone).containsKey(nro.identifiant)){
                            NRA nraEquiv = listeNRA.get(autreZone).get(nro.identifiant); // on recherche les NRO d'une autre zone qui auraient regroupé un NRA de même code
                            for (NRA nroAutre : listeNRO.get(autreZone)){
                                if (nroAutre.hasNRA(nraEquiv) && nroAutre.podi() > podiMax){ // on considère en tant que NRO parmi ceux ayant regroupé un NRA de même code celui qui a le plus grand PODI
                                    podiMax = nroAutre.podi();
                                    nouveauNRO = nroAutre;
                                    System.out.println("Nouveau NRO trouvé !");
                                    break;
                                }
                            }
                        }
                    }
                    if (nouveauNRO!=null){ // si on en a trouvé un, on va effectuer un rattachement des lignes
                        System.out.println("Le NRA "+nro.identifiant+" en zone "+zone+" n'est plus NRO et se rattache désormais au NRO "+nouveauNRO.identifiant+" de la zone "+nouveauNRO.zone);
                        iterator.remove();
                        Set<NRA> liberes = nro.libereNRAMultiples(listeNRA); // les NRA déjà regroupés par le proto-NRO qui existait étaient forcement eux aussi en dessous du seuil de lignes et on doit donc les libérer de ce proto-NRO afin ensuite d'analyser si pour ces NRA il existe un NRO de même code dans une autre zone
                        liberes.remove(nro);
                        for (NRA nra : liberes){ 
                            iterator.add(nra);
                            iterator.previous();
                        }
                        nro.setNewZone(nouveauNRO.zone);
                        nouveauNRO.addToEquivalent(nro);
                    }
                }
            }
            this.listeNRO.replace(zone, newListeNRO);
        }
    }

    public List<PC> getListePC(){
        return this.listePC;
    }

    public void filtreCommunes(Set<String> listeCommunes){
        try {
            double[] coord = new double[2];
            for (Map<String, NRA> liste : listeNRA.values()){
                for (NRA nra : liste.values()){
                    if (!listeCommunes.contains(nra.identifiant.substring(0,5))){
                        liste.remove(nra.identifiant);
                        coord[0]=nra.x;
                        coord[1]=nra.y;
                        indexNRA.delete(coord);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    class Comparateur implements Comparator<NRA> { // permet le maintien du tri à l'intérieur de chaque file de priorité pour le regroupement

        @Override
        public int compare(NRA o1, NRA o2) {
            return o1.podi()-o2.podi();

        }
    }
    
}
