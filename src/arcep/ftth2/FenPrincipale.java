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

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import arcep.ftth2.configuration.Configuration;
import arcep.utilitaires.monitoring.MonitoredThread;


@SuppressWarnings({"serial", "unchecked", "rawtypes"})
public class FenPrincipale extends JFrame {
    
    private final String cheminReseau;
    private final String adresseShapesGC;
    private final String adresseShapesRoutes;
    private final String fileDpts;
    private ArrayList<String> listeDpt;
    private ArrayList<String> listeDptRoutier;
    private final ModuleTopo topo;
    private final String[] demandeCible;
    private final String fichierImmeubles, fichierCoutsUnitaires, dossierCommunes, racineResultats;
    private HashMap<String, Double> parametresReseau;
    
    public FenPrincipale(){
    	String adresseShapesGC = Configuration.get().adresseShapesGC;
    	String adresseShapesRoutes = Configuration.get().adresseShapesRoutes;
        String[] fichiersCuivre = Configuration.get().fichiersCuivre;
        String adresseDptsLimitrophes = Configuration.get().adresseDptsLimitrophes;
        String adresseShapesDptsMetro = Configuration.get().adresseShapesDptsMetro;
        String[] shapesDOM = Configuration.get().shapesDOM;
        String nameShapeDpts = Configuration.get().nameShapeDpts;
        String[] fichiersZonage = Configuration.get().fichiersZonage;
        String[] demandeCible = Configuration.get().demandeCible;
        String fichierImmeubles = Configuration.get().fichierImmeubles;
        String fichierCoutsUnitaires  = Configuration.get().fichierCoutsUnitaires;
        String racineResultats = Configuration.get().racineResultats;
        HashMap<String, Double> parametresReseau = Configuration.get().parametresReseau;
    	
    	
        //Formatage de la fenêtre
        initComponents();
        this.setTitle("Arcep - modèle de réseau BLOM pour la tarification du dégroupage");
        
        // Le menu de sélection du diamètre de câble minimal utilisé en horizontal
        // est initialisé avec la valeur "12 FO" par défaut, conformément à la
        // valeur retenue pour la version 1.2 d'avril 2020
        jComboBox3.setSelectedItem("12 FO");
        jComboBox4.setSelectedItem("En souterrain");
        jComboBox5.setSelectedItem("En aérien");
        
        // initailisation des adresses principales
        this.cheminReseau = Configuration.get().cheminReseau;
        File dir = new File(cheminReseau);
        dir.mkdirs();
        this.adresseShapesGC = adresseShapesGC;
        this.adresseShapesRoutes = adresseShapesRoutes;
        this.fileDpts = "liste-departements-reseau-disponible.csv"; // fichier créé lors de la première utilisation du modèle
        this.demandeCible = demandeCible;
        this.fichierImmeubles = fichierImmeubles;
        this.fichierCoutsUnitaires = fichierCoutsUnitaires;
        this.dossierCommunes = fichiersZonage[2];
        this.racineResultats = racineResultats;
        
        this.parametresReseau = parametresReseau;

        // initialisation du module topo
        File fichierSR = new File(fichiersCuivre[1]);
        if (!fichierSR.exists()){
            this.jCheckBox11.setEnabled(false);
        }
        topo = new ModuleTopo(fichiersCuivre, adresseDptsLimitrophes, adresseShapesDptsMetro, shapesDOM, nameShapeDpts,
                fichiersZonage);
        topo.readDptsLimitrophes();
        
        //Formatage et chargement du tableau de coûts unitaires (pas utilisé dans la version actuelle)
        this.loadUnitCosts(fichierCoutsUnitaires);
        
        setLocationRelativeTo(null);//Centrer la fenêtre
        setVisible(true);
        this.initListesDpts();
    }
    
    private void initListesDpts(){
        listeDpt = new ArrayList<>();
        listeDptRoutier = new ArrayList<>();
        try{
            BufferedReader dpts = new BufferedReader(new FileReader(cheminReseau+fileDpts));
            dpts.readLine();
            String line;
            while((line = dpts.readLine()) != null){
                String[] fields = line.split(";");
                String dpt = fields[0];
                listeDpt.add(dpt + " | " + Configuration.get().getNomDepartement(dpt) + "    ");
                if (fields[1].equals("routier"))
                    listeDptRoutier.add(dpt + " | " + Configuration.get().getNomDepartement(dpt) + "    ");
            }
            dpts.close();
        } catch(Exception e){
            JOptionPane.showMessageDialog(null, "Avant toute chose, cliquez sur le bouton \"Mettre à jour les départements disponibles\" de l'onglet choix des départements !");
        }

        Collections.sort(listeDpt);
        jList1.setListData(listeDpt.toArray());

        jList1.setCellRenderer(new DefaultListCellRenderer() {
                    @Override
                    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                            String s = value.toString();
                            if (listeDptRoutier.contains(s)) {
                                    this.setForeground(Color.RED);
                                    this.setFont(this.getFont().deriveFont(Font.ITALIC));
                            }
                            return this;
                    }
            });
    }

    private void loadUnitCosts(String file){
        try {
            Vector<String> nomColonne = new Vector<>();
            Vector<Vector<String>> donnees = new Vector<>();
            String ligne, donneesLigne[];

            BufferedReader ficCU = new BufferedReader(new FileReader(file));

            donneesLigne = ficCU.readLine().split(";");
            for (int j = 0; j < 5; j++) {
                nomColonne.add(donneesLigne[j]);
            }

            while ((ligne = ficCU.readLine()) != null) {
                donneesLigne = ligne.split(";");
                Vector<String> cout = new Vector<>();

                for (int j = 0; j < 5; j++) {
                    cout.add(donneesLigne[j]);
                }
                donnees.add(cout);
            }
            ficCU.close();

            DefaultTableModel dtm = new DefaultTableModel(donnees, nomColonne) {

                @Override
                public boolean isCellEditable(int rowIndex, int columnIndex) {
                    return columnIndex >= 4;
                }
            };

            jTable1.setModel(dtm);
            jTable1.getColumnModel().getColumn(0).setMaxWidth(100);
            jTable1.getColumnModel().getColumn(1).setMaxWidth(150);
            jTable1.getColumnModel().getColumn(3).setMaxWidth(100);
            jTable1.getColumnModel().getColumn(4).setMaxWidth(100);

        } catch (Exception e) {
            System.out.println("Pas de fichier de coûts unitaire à l'adresse : "+file);
        }
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        buttonGroup2 = new javax.swing.ButtonGroup();
        buttonGroup3 = new javax.swing.ButtonGroup();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jList1 = new javax.swing.JList();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jCheckBox6 = new javax.swing.JCheckBox();
        jCheckBox7 = new javax.swing.JCheckBox();
        jCheckBox8 = new javax.swing.JCheckBox();
        jCheckBox9 = new javax.swing.JCheckBox();
        jLabel8 = new javax.swing.JLabel();
        jButton14 = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        jTextField16 = new javax.swing.JTextField();
        jLabel24 = new javax.swing.JLabel();
        jButton5 = new javax.swing.JButton();
        jButton8 = new javax.swing.JButton();
        jLabel23 = new javax.swing.JLabel();
        jButton11 = new javax.swing.JButton();
        jButton12 = new javax.swing.JButton();
        jButton13 = new javax.swing.JButton();
        jButton15 = new javax.swing.JButton();
        jCheckBox12 = new javax.swing.JCheckBox();
        jLabel30 = new javax.swing.JLabel();
        jTextField20 = new javax.swing.JTextField();
        jLabel31 = new javax.swing.JLabel();
        jLabel33 = new javax.swing.JLabel();
        jLabel35 = new javax.swing.JLabel();
        jTextField22 = new javax.swing.JTextField();
        jTextField24 = new javax.swing.JTextField();
        jCheckBox13 = new javax.swing.JCheckBox();
        jCheckBox11 = new javax.swing.JCheckBox();
        jCheckBox5 = new javax.swing.JCheckBox();
        jLabel14 = new javax.swing.JLabel();
        jLabel20 = new javax.swing.JLabel();
        jLabel22 = new javax.swing.JLabel();
        jLabel25 = new javax.swing.JLabel();
        jLabel29 = new javax.swing.JLabel();
        jLabel34 = new javax.swing.JLabel();
        jSeparator3 = new javax.swing.JSeparator();
        jSeparator4 = new javax.swing.JSeparator();
        jCheckBox16 = new javax.swing.JCheckBox();
        jCheckBox17 = new javax.swing.JCheckBox();
        jPanel2 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        jTextField2 = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        jTextField4 = new javax.swing.JTextField();
        jLabel7 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        jLabel15 = new javax.swing.JLabel();
        jTextField9 = new javax.swing.JTextField();
        jLabel16 = new javax.swing.JLabel();
        jTextField10 = new javax.swing.JTextField();
        jLabel17 = new javax.swing.JLabel();
        jTextField11 = new javax.swing.JTextField();
        jLabel18 = new javax.swing.JLabel();
        jTextField12 = new javax.swing.JTextField();
        jCheckBox1 = new javax.swing.JCheckBox();
        jCheckBox2 = new javax.swing.JCheckBox();
        jCheckBox3 = new javax.swing.JCheckBox();
        jCheckBox4 = new javax.swing.JCheckBox();
        jButton3 = new javax.swing.JButton();
        jComboBox1 = new javax.swing.JComboBox();
        jLabel19 = new javax.swing.JLabel();
        jCheckBox10 = new javax.swing.JCheckBox();
        jLabel10 = new javax.swing.JLabel();
        jTextField5 = new javax.swing.JTextField();
        jLabel26 = new javax.swing.JLabel();
        jTextField18 = new javax.swing.JTextField();
        jLabel27 = new javax.swing.JLabel();
        jTextField19 = new javax.swing.JTextField();
        jLabel28 = new javax.swing.JLabel();
        jComboBox2 = new javax.swing.JComboBox();
        jLabel32 = new javax.swing.JLabel();
        jTextField21 = new javax.swing.JTextField();
        jLabel36 = new javax.swing.JLabel();
        jLabel37 = new javax.swing.JLabel();
        jTextField25 = new javax.swing.JTextField();
        jLabel38 = new javax.swing.JLabel();
        jTextField26 = new javax.swing.JTextField();
        jLabel39 = new javax.swing.JLabel();
        jTextField27 = new javax.swing.JTextField();
        jLabel40 = new javax.swing.JLabel();
        jTextField28 = new javax.swing.JTextField();
        jLabel41 = new javax.swing.JLabel();
        jTextField29 = new javax.swing.JTextField();
        jLabel42 = new javax.swing.JLabel();
        jLabel43 = new javax.swing.JLabel();
        jTextField30 = new javax.swing.JTextField();
        jLabel44 = new javax.swing.JLabel();
        jTextField31 = new javax.swing.JTextField();
        jLabel45 = new javax.swing.JLabel();
        jTextField32 = new javax.swing.JTextField();
        jLabel48 = new javax.swing.JLabel();
        jTextField35 = new javax.swing.JTextField();
        jLabel21 = new javax.swing.JLabel();
        jTextField3 = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        jComboBox3 = new javax.swing.JComboBox();
        jLabel49 = new javax.swing.JLabel();
        jRadioButton1 = new javax.swing.JRadioButton();
        jRadioButton2 = new javax.swing.JRadioButton();
        jLabel11 = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        jCheckBox14 = new javax.swing.JCheckBox();
        jCheckBox15 = new javax.swing.JCheckBox();
        jComboBox4 = new javax.swing.JComboBox<>();
        jLabel46 = new javax.swing.JLabel();
        jComboBox5 = new javax.swing.JComboBox<>();
        jPanel3 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        jPanel10 = new javax.swing.JPanel();
        jButton4 = new javax.swing.JButton();
        jButton6 = new javax.swing.JButton();
        jButton7 = new javax.swing.JButton();
        jPanel5 = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        jEditorPane1 = new javax.swing.JEditorPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("ARCEP - Modèle FttH");
        setResizable(false);

        jTabbedPane1.setTabLayoutPolicy(javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT);
        jTabbedPane1.setMaximumSize(new java.awt.Dimension(100, 150));
        jTabbedPane1.setMinimumSize(new java.awt.Dimension(100, 150));

        jList1.setLayoutOrientation(javax.swing.JList.VERTICAL_WRAP);
        jList1.setVisibleRowCount(35);
        jScrollPane1.setViewportView(jList1);

        jButton1.setText("Tout sélectionner");
        jButton1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton1MouseClicked(evt);
            }
        });

        jButton2.setText("Tout désélectionner");
        jButton2.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton2MouseClicked(evt);
            }
        });

        jCheckBox6.setText("Liste de communes spécifique");

        jCheckBox7.setSelected(true);
        jCheckBox7.setText("Zone RIP");

        jCheckBox8.setSelected(true);
        jCheckBox8.setText("Zone AMII");

        jCheckBox9.setSelected(true);
        jCheckBox9.setText("Zone très dense");

        jLabel8.setFont(jLabel8.getFont().deriveFont(jLabel8.getFont().getStyle() | java.awt.Font.BOLD));
        jLabel8.setText("Périmètre de déploiement");

        jButton14.setText("<html> <center> Mettre à jour les <br> départements disponibles </center>");
        jButton14.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton14MouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 499, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jCheckBox6)
                    .addComponent(jCheckBox7)
                    .addComponent(jCheckBox8)
                    .addComponent(jCheckBox9)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(jButton1, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jButton2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jButton14, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                        .addComponent(jLabel8, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap(463, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 642, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jButton1)
                        .addGap(18, 18, 18)
                        .addComponent(jButton2)
                        .addGap(18, 18, 18)
                        .addComponent(jButton14, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jLabel8)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jCheckBox9)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jCheckBox8)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jCheckBox7)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jCheckBox6)))
                .addContainerGap())
        );

        jTabbedPane1.addTab("Choix des départements", jPanel1);

        jTextField16.setText("3");

        jLabel24.setText("mètres");

        jButton5.setText("Module topo et sortie des fichiers intermédiaires \"BLO\"");
        jButton5.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton5MouseClicked(evt);
            }
        });

        jButton8.setText("Regroupement des NRA en NRO et création des shapefiles des ZANRO");
        jButton8.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton8MouseClicked(evt);
            }
        });

        jLabel23.setText("Tolérance pour l'accrochage des noeuds du graphe :");

        jButton11.setText("Préparation des fichiers du réseau cuivre");
        jButton11.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton11MouseClicked(evt);
            }
        });

        jButton12.setText("Préparation shapes (départements, zonage)");
        jButton12.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton12MouseClicked(evt);
            }
        });

        jButton13.setText("Création des shapefiles des ZANRA");
        jButton13.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton13MouseClicked(evt);
            }
        });

        jButton15.setText("Pré-traitement réseau de collecte");
        jButton15.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton15MouseClicked(evt);
            }
        });

        jCheckBox12.setSelected(true);
        jCheckBox12.setText("Charger les réseaux des départements limitrophes");

        jLabel30.setText("Tolérance de distance max. pour être raccordé au GC :");

        jTextField20.setText("15");

        jLabel31.setText("mètres");

        jLabel33.setText("Nombre minimal de lignes pour un NRO :");

        jLabel35.setText("Distance maximale NRO-NRA (km) :");

        jTextField22.setText("1000");

        jTextField24.setText("15");

        jCheckBox13.setSelected(true);
        jCheckBox13.setText("Utiliser le GC d'Orange");

        jCheckBox11.setText("<html>\n\nUtiliser les sous-répartiteurs lors de la lecture du réseau cuivre pour la création des ZANRA</br>\net le regroupement des NRA en NRO");

        jCheckBox5.setText("Supprimer les shapefiles préexistants pour la création des ZANRA");

        jLabel14.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jLabel14.setText("Module A : prétraitement");

        jLabel20.setText("<html>Fichiers de sortie :\n<ul style=\"list-style-type:circle\">\n<li>Préparation shapes : DptsEtendus-5km</li>\n<li>Préparation des fichiers du réseau cuivre : LP, PC1, NRA, SR, PC2</li>\n<li>Traitement des réseaux de collecte : Collecte</li>\n<li>Création des shapefiles des ZANRA : VoronoiPC, ZANRA</li>\n</ul>\n");
        jLabel20.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        jLabel20.setPreferredSize(new java.awt.Dimension(400, 90));

        jLabel22.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jLabel22.setText("Module B : regroupement");

        jLabel25.setText("<html>\nFichiers de sortie :\n<ul style=\"list-style-type:circle\">\n<li>NRO-XXkm/[dept]/[zone]/ListePC</li>\n<li>ZANRO-XXkm/</li>\n</ul>");
        jLabel25.setPreferredSize(new java.awt.Dimension(400, 14));

        jLabel29.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jLabel29.setText("Module C : réseau physique");

        jLabel34.setText("<html>\nFichiers de sortie :\n<ul style=\"list-style-type:circle\">\n<li>BLO-[GC-][routier-]XXkm/[dept]/[zone]/Arêtes</li>\n<li>BLO-[GC-][routier-]XXkm/[dept]/[zone]/Noeuds</li>\n<li>BLO-[GC-][routier-]XXkm/[dept]/[zone]/ModesPose</li>\n</ul>");
        jLabel34.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        jLabel34.setPreferredSize(new java.awt.Dimension(400, 14));

        jCheckBox16.setSelected(true);
        jCheckBox16.setText("Utiliser les routes");

        jCheckBox17.setText("Tracé shapefile du graphe représentant le réseau");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(34, 34, 34)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jSeparator3, javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(jButton12, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGroup(jPanel4Layout.createSequentialGroup()
                                    .addComponent(jButton11, javax.swing.GroupLayout.DEFAULT_SIZE, 240, Short.MAX_VALUE)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 17, Short.MAX_VALUE)
                                    .addComponent(jButton15, javax.swing.GroupLayout.PREFERRED_SIZE, 198, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addComponent(jButton8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jButton13, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jCheckBox11, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                                .addComponent(jButton5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGroup(jPanel4Layout.createSequentialGroup()
                                    .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(jPanel4Layout.createSequentialGroup()
                                            .addComponent(jLabel30)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                            .addComponent(jTextField20, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                            .addComponent(jLabel31))
                                        .addGroup(jPanel4Layout.createSequentialGroup()
                                            .addComponent(jLabel23, javax.swing.GroupLayout.PREFERRED_SIZE, 265, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                            .addComponent(jTextField16, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                            .addComponent(jLabel24))
                                        .addGroup(jPanel4Layout.createSequentialGroup()
                                            .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                .addComponent(jLabel33, javax.swing.GroupLayout.PREFERRED_SIZE, 206, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addComponent(jLabel35))
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                            .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                .addComponent(jTextField24, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addComponent(jTextField22, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                                    .addGap(0, 0, Short.MAX_VALUE))
                                .addGroup(jPanel4Layout.createSequentialGroup()
                                    .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jCheckBox16)
                                        .addComponent(jCheckBox13))
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jCheckBox12, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 282, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jCheckBox17, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 282, javax.swing.GroupLayout.PREFERRED_SIZE))))
                            .addComponent(jCheckBox5))
                        .addGap(50, 50, 50)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jLabel14)
                            .addComponent(jLabel22)
                            .addComponent(jLabel29)
                            .addComponent(jLabel25, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel20, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel34, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addComponent(jSeparator4))
                .addGap(0, 218, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(27, 27, 27)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton12)
                    .addComponent(jLabel14))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jButton11)
                            .addComponent(jButton15))
                        .addGap(8, 8, 8)
                        .addComponent(jCheckBox11, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jCheckBox5)
                        .addGap(8, 8, 8)
                        .addComponent(jButton13))
                    .addComponent(jLabel20, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(40, 40, 40)
                .addComponent(jSeparator4, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel33)
                    .addComponent(jTextField22, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel22))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel35)
                            .addComponent(jTextField24, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton8, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jLabel25, javax.swing.GroupLayout.PREFERRED_SIZE, 62, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(40, 40, 40)
                .addComponent(jSeparator3, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel23)
                    .addComponent(jTextField16, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel24)
                    .addComponent(jLabel29))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel30)
                            .addComponent(jTextField20, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel31))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jCheckBox12)
                            .addComponent(jCheckBox13))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jCheckBox16)
                            .addComponent(jCheckBox17))
                        .addGap(12, 12, 12)
                        .addComponent(jButton5, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jLabel34, javax.swing.GroupLayout.PREFERRED_SIZE, 91, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(131, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Module topologique", jPanel4);

        jPanel2.setMaximumSize(new java.awt.Dimension(0, 0));

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setText("Placement des points de mutualisation (PM)");
        jLabel1.setFocusable(false);
        jLabel1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);

        jLabel2.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel2.setFocusable(false);
        jLabel2.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);

        jLabel3.setText("Taille minimale des PM en ZMD");

        jTextField1.setText("300");

        jLabel4.setText("maximale de la distance PM-PBO pour chaque ZAPM (en mètres)");

        jTextField2.setText("5000");

        jLabel6.setText("Taille minimale des PM extérieurs en poches de haute densité de la ZTD");

        jTextField4.setText("100");

        jLabel7.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jLabel7.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel7.setText("Amont des PM - transport optique");
        jLabel7.setFocusable(false);
        jLabel7.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);

        jLabel9.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jLabel9.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel9.setText("Fichiers de sortie détaillés");
        jLabel9.setFocusable(false);
        jLabel9.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);

        jLabel12.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jLabel12.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel12.setText("Paramètres de calcul des unités d'oeuvre");
        jLabel12.setFocusable(false);
        jLabel12.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);

        jLabel15.setText("Taux appliqué aux nombre de lignes pour le dimensionnement du transport optique");

        jTextField9.setText("10%");

        jLabel16.setText("Nb min de fibres par PM300");

        jTextField10.setText("36");

        jLabel17.setText("Surcapacité en distribution");

        jTextField11.setText("15%");

        jLabel18.setText("Surcapacité en transport");

        jTextField12.setText("0%");

        jCheckBox1.setText("Unités d'oeuvre par PM");

        jCheckBox2.setText("PM");

        jCheckBox3.setText("Aretes");

        jCheckBox4.setText("Coûts par NRO");

        jButton3.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jButton3.setText("Lancer la modélisation");
        jButton3.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton3MouseClicked(evt);
            }
        });

        jComboBox1.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "720 FO", "576 FO", "288 FO" }));

        jLabel19.setText("Taille max. des câbles en souterrain");

        jCheckBox10.setText("Statistiques de longueurs");

        jLabel10.setText("Taille minimale des PM en poches de basse densité de la ZTD");

        jTextField5.setText("300");

        jLabel26.setText("Distance max inter boitiers (m)");

        jTextField18.setText("1000");

        jLabel27.setText("Nb min de fibres par PM100");

        jTextField19.setText("12");

        jLabel28.setText("en aérien");

        jComboBox2.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "288 FO", "144 FO", "96 FO" }));

        jLabel32.setText("Mark-up longueur de câbles");

        jTextField21.setText("10%");

        jLabel36.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel36.setText("Nb max de ligne par armoire de PM");

        jLabel37.setText("En ZMD :");

        jTextField25.setText("400");

        jLabel38.setText("En ZTD_BD :");

        jTextField26.setText("400");

        jLabel39.setText("En ZTD_HD :");

        jTextField27.setText("120");

        jLabel40.setText("PMint en ZTD_HD :");

        jTextField28.setText("24");

        jLabel41.setText("Nb max de lignes par PBO :");

        jTextField29.setText("8");

        jLabel42.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel42.setText("Au NRO");

        jLabel43.setText("Nb de fibres par tiroir :");

        jTextField30.setText("144");

        jLabel44.setText("Nb de tiroirs par RTO :");

        jTextField31.setText("8");

        jLabel45.setText("Surface dun RTO (m) :");

        jTextField32.setText("0,56");

        jLabel48.setText("Coefficient multiplicateur surface :");

        jTextField35.setText("3,0");

        jLabel21.setText("Mode pour le GC à construire en distribution");

        jTextField3.setText("12");

        jLabel5.setText("Taille minimale des PM intérieurs");

        jComboBox3.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "24 FO", "12 FO", "6 FO" }));

        jLabel49.setText("Taille min. des câbles en horizontal");

        buttonGroup3.add(jRadioButton1);
        jRadioButton1.setText("Médiane");

        buttonGroup3.add(jRadioButton2);
        jRadioButton2.setSelected(true);
        jRadioButton2.setText("Moyenne");

        jLabel11.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel11.setText("Fichiers CSV :");

        jLabel13.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel13.setText("Shapefiles :");

        jCheckBox14.setText("NRO");

        jCheckBox15.setText("PBO");

        jComboBox4.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "En aérien", "En souterrain", "Tout reconstruire en souterrain" }));
        jComboBox4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox4ActionPerformed(evt);
            }
        });

        jLabel46.setText("Mode pour le GC à construire en transport");

        jComboBox5.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "En aérien", "En souterrain" }));

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 659, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(9, 9, 9))
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(jLabel7, javax.swing.GroupLayout.PREFERRED_SIZE, 659, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel12, javax.swing.GroupLayout.PREFERRED_SIZE, 705, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(jRadioButton1)
                                .addGap(6, 6, 6)
                                .addComponent(jRadioButton2)
                                .addGap(6, 6, 6)
                                .addComponent(jLabel4)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 659, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addGap(1, 1, 1)
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel16, javax.swing.GroupLayout.PREFERRED_SIZE, 133, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jTextField10, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(28, 28, 28)
                                        .addComponent(jLabel27)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jTextField19, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel15)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jTextField9, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE))))
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(jLabel19, javax.swing.GroupLayout.PREFERRED_SIZE, 175, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jLabel28)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jLabel49)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jComboBox3, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(jLabel17)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jTextField11, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jTextField12, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(50, 50, 50)
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel21)
                                    .addComponent(jLabel46))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(jComboBox4, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(jComboBox5, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel6)
                                    .addComponent(jLabel10)
                                    .addComponent(jLabel3))
                                .addGap(18, 18, 18)
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addComponent(jTextField1)
                                    .addComponent(jTextField5, javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jTextField4, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 32, Short.MAX_VALUE)
                                    .addComponent(jTextField3)))
                            .addComponent(jLabel18)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(jLabel26)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jTextField18, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jLabel32, javax.swing.GroupLayout.PREFERRED_SIZE, 136, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jTextField21, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jLabel41)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jTextField29, javax.swing.GroupLayout.PREFERRED_SIZE, 18, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addComponent(jButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 176, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(jLabel5)
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel36)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(jLabel37)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jTextField25, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(jLabel38)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jTextField26, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(jLabel39)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jTextField27, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(18, 18, 18)
                                        .addComponent(jLabel40)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jTextField28, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(jLabel11)
                                            .addComponent(jLabel13))
                                        .addGap(18, 18, 18)
                                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(jCheckBox1)
                                            .addGroup(jPanel2Layout.createSequentialGroup()
                                                .addComponent(jCheckBox14)
                                                .addGap(20, 20, 20)
                                                .addComponent(jCheckBox2)
                                                .addGap(20, 20, 20)
                                                .addComponent(jCheckBox15)))
                                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addGroup(jPanel2Layout.createSequentialGroup()
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jCheckBox4)
                                                .addGap(20, 20, 20)
                                                .addComponent(jCheckBox10))
                                            .addGroup(jPanel2Layout.createSequentialGroup()
                                                .addGap(20, 20, 20)
                                                .addComponent(jCheckBox3))))
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel42)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(jLabel43)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jTextField30, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(jLabel44)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jTextField31, javax.swing.GroupLayout.PREFERRED_SIZE, 18, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(jLabel45)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jTextField32, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(jLabel48)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jTextField35, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addComponent(jLabel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))))
                .addContainerGap(452, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addGap(27, 27, 27)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jRadioButton1)
                        .addComponent(jRadioButton2)
                        .addComponent(jLabel4))
                    .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(6, 6, 6)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel10)
                    .addComponent(jTextField5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(jTextField4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(jTextField3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(jLabel7)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel15)
                    .addComponent(jTextField9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel16)
                    .addComponent(jTextField10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel27)
                    .addComponent(jTextField19, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(jLabel12)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel19)
                    .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel28)
                    .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel49)
                    .addComponent(jComboBox3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel17)
                    .addComponent(jTextField11, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel21)
                    .addComponent(jComboBox5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel18)
                    .addComponent(jTextField12, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel46)
                    .addComponent(jComboBox4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel26)
                    .addComponent(jTextField18, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel32)
                    .addComponent(jTextField21, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel41)
                    .addComponent(jTextField29, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel36)
                    .addComponent(jLabel37)
                    .addComponent(jTextField25, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel38)
                    .addComponent(jTextField26, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel39)
                    .addComponent(jTextField27, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel40)
                    .addComponent(jTextField28, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel42)
                    .addComponent(jLabel43)
                    .addComponent(jTextField30, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel44)
                    .addComponent(jTextField31, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel45)
                    .addComponent(jTextField32, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel48)
                    .addComponent(jTextField35, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(jLabel9)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jCheckBox4)
                    .addComponent(jCheckBox10)
                    .addComponent(jCheckBox1)
                    .addComponent(jLabel11))
                .addGap(6, 6, 6)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jCheckBox2)
                    .addComponent(jCheckBox3)
                    .addComponent(jLabel13)
                    .addComponent(jCheckBox14)
                    .addComponent(jCheckBox15))
                .addGap(18, 18, 18)
                .addComponent(jButton3)
                .addGap(65, 65, 65))
        );

        jTabbedPane1.addTab("Module de déploiement", jPanel2);

        jPanel3.setLayout(new java.awt.BorderLayout());

        jScrollPane2.setViewportView(jTable1);

        jPanel3.add(jScrollPane2, java.awt.BorderLayout.CENTER);

        jButton4.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jButton4.setText("Sauvegarder les coûts unitaires par défaut");
        jButton4.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton4MouseClicked(evt);
            }
        });

        jButton6.setText("Ouvrir un scénario de coûts unitaires");
        jButton6.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton6MouseClicked(evt);
            }
        });

        jButton7.setText("Sauvegarder un scénario de coûts unitaires");
        jButton7.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jButton7MouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jButton6, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jButton7, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 623, Short.MAX_VALUE)
                .addComponent(jButton4)
                .addContainerGap())
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel10Layout.createSequentialGroup()
                .addContainerGap(37, Short.MAX_VALUE)
                .addComponent(jButton6)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton4)
                    .addComponent(jButton7))
                .addContainerGap())
        );

        jPanel3.add(jPanel10, java.awt.BorderLayout.PAGE_END);

        jTabbedPane1.addTab("Coûts unitaires", jPanel3);

        jPanel5.setLayout(new java.awt.BorderLayout());

        jEditorPane1.setEditable(false);
        jEditorPane1.setContentType("text/html"); // NOI18N
        jEditorPane1.setText("<html>   <head>    </head>   <body>  <b>Modèle réseau d'accès BLOM et tarification du dégroupage v1.6 du 05/12/2024</b><br> <br> Direction économie, marchés et numérique<br> Autorité de régulation des communications électroniques, des postes et de la distribution de la presse<br> République française<br> <br> <i>Utilisation par un tiers possible sous la licence BSD 2-clause \"Simplified\" License détaillée en tête de chaque fichier source</i><br> <br><br><br> Ce modèle utilise les bibliothèques libres suivantes :<br> <br> •\tGeoTools<br> Traitement de fichiers et de données géographiques -  Licence publique générale limitée GNU<br> Utilisation de la version 17.1<br> http://geotools.org/<br> <br> •\tJGraphT<br> Structures et algorithmes de graphes – Licence publique générale limitée GNU ou Licence Publique Eclipse<br> http://www.jgrapht.org/<br> <br> •\tGuava<br> Utilitaires, classes génériques et autres fonctionnalités -  Licence Apache 2.0<br> https://github.com/google/guava/<br> <br> •\tKD-Tree<br> Indexation spatiale -  Licence publique générale limitée GNU<br> http://home.wlu.edu/~levys/software/kd/<br> <br>   </body> </html>  ");
        jScrollPane3.setViewportView(jEditorPane1);

        jPanel5.add(jScrollPane3, java.awt.BorderLayout.CENTER);

        jTabbedPane1.addTab("Crédits", jPanel5);

        getContentPane().add(jTabbedPane1, java.awt.BorderLayout.PAGE_START);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
    * Clic sur le bouton "Tout sélectionner" (onglet "Choix des départements")
    */
    private void jButton1MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton1MouseClicked
        jList1.setSelectionInterval(0, jList1.getModel().getSize() - 1);
}//GEN-LAST:event_jButton1MouseClicked
    /**
    * Clic sur le bouton "Tout désélectionner" (onglet "Choix des départements")
    */
    private void jButton2MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton2MouseClicked
        jList1.clearSelection();
    }//GEN-LAST:event_jButton2MouseClicked

    /**
    * Clic sur le bouton "Ouvrir un scénario de coûts unitaires" (onglet "Coûts unitaires")
    */
    private void jButton6MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton6MouseClicked
        //Charger des coûts unitaires
        int reponse = JOptionPane.showConfirmDialog(this, "Si les coûts unitaires n'ont pas été sauvegardés, ils seront perdus.\nContinuer ?", "Ouvrir des coûts unitaires", JOptionPane.YES_NO_OPTION);
        if (reponse == JOptionPane.YES_OPTION) {
            final JFileChooser fc = new JFileChooser("Sources");
            FileFilter filtre = new FileNameExtensionFilter("Fichier CSV", "csv");
            fc.addChoosableFileFilter(filtre);
            int returnVal = fc.showOpenDialog(this);

            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                try {
                    Vector<String> nomColonne = new Vector<>();
                    Vector<Vector<String>> donnees = new Vector<>();
                    String ligne, donneesLigne[];

                    BufferedReader ficCU = new BufferedReader(new FileReader(file));

                    donneesLigne = ficCU.readLine().split(";");
                    for (int j = 0; j < 5; j++) {
                        nomColonne.add(donneesLigne[j]);
                    }

                    while ((ligne = ficCU.readLine()) != null) {
                        donneesLigne = ligne.split(";");
                        Vector<String> cout = new Vector<>();

                        for (int j = 0; j < 5; j++) {
                            cout.add(donneesLigne[j]);
                        }
                        donnees.add(cout);
                    }
                    ficCU.close();

                    DefaultTableModel dtm = new DefaultTableModel(donnees, nomColonne) {

                        public boolean isCellEditable(int rowIndex, int columnIndex) {
                            if (columnIndex < 4) {
                                return false;
                            } else {
                                return true;
                            }
                        }
                    };

                    jTable1.setModel(dtm);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }//GEN-LAST:event_jButton6MouseClicked

    /**
    * Clic sur le bouton "Sauvegarder un scénario de coûts unitaires" (onglet "Coûts unitaires")
    */
    private void jButton7MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton7MouseClicked
        final JFileChooser fc = new JFileChooser("Sources");
        FileFilter filtre = new FileNameExtensionFilter("Fichier CSV", "csv");
        fc.addChoosableFileFilter(filtre);
        int returnVal = fc.showSaveDialog(this);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            boolean ecraserFichier = true;

            if (file.exists()) {
                int reponse = JOptionPane.showConfirmDialog(this, "Le fichier existe déjà. Voulez-vous le remplacer ?", "Fichier existant", JOptionPane.YES_NO_OPTION);
                if (reponse == JOptionPane.NO_OPTION) {
                    ecraserFichier = false;
                }
            }

            if (!file.exists() || ecraserFichier) {
                try {
                    int row = jTable1.getEditingRow();
                    int col = jTable1.getEditingColumn();
                    if (row != -1 && col != -1) jTable1.getCellEditor(row,col).stopCellEditing();

                    PrintWriter csvCU = new PrintWriter(new FileWriter(file));
                    csvCU.println("Catégorie;ID;Description;Unité;Valeur");

                    TableModel modele = jTable1.getModel();


                    for (int i = 0; i < modele.getRowCount(); i++) {
                        String ligneCSV = "";
                        for (int j = 0; j < modele.getColumnCount(); j++) {
                            if (j == 0) {
                                ligneCSV += modele.getValueAt(i, j).toString();
                            } else {
                                ligneCSV += ";" + modele.getValueAt(i, j).toString();
                            }
                        }
                        csvCU.println(ligneCSV);
                    }
                    csvCU.close();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }//GEN-LAST:event_jButton7MouseClicked

    /**
    * Clic sur le bouton "Sauvegarder les coûts unitaires par défaut" (onglet "Coûts unitaires")
    */
    private void jButton4MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton4MouseClicked

        int reponse = JOptionPane.showConfirmDialog(this, "Les coûts unitaires par défault vont être remplacés, continuer ?", "Fichier existant", JOptionPane.YES_NO_OPTION);
        if (reponse == JOptionPane.YES_OPTION) {

            try {
                int row = jTable1.getEditingRow();
                int col = jTable1.getEditingColumn();
                if (row != -1 && col != -1) jTable1.getCellEditor(row,col).stopCellEditing();

                PrintWriter csvCU = new PrintWriter(new FileWriter(fichierCoutsUnitaires));
                csvCU.println("Catégorie;ID;Description;Unité;Valeur");

                TableModel modele = jTable1.getModel();


                for (int i = 0; i < modele.getRowCount(); i++) {
                    String ligneCSV = "";
                    for (int j = 0; j < modele.getColumnCount(); j++) {
                        if (j == 0) {
                            ligneCSV += modele.getValueAt(i, j).toString();
                        } else {
                            ligneCSV += ";" + modele.getValueAt(i, j).toString();
                        }
                    }
                    csvCU.println(ligneCSV);
                }
                csvCU.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }//GEN-LAST:event_jButton4MouseClicked

    /**
    * Clic sur le bouton "Préparation shapes (départements, zonage)" (onglet "Module topologique")
    */
    private void jButton12MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton12MouseClicked
        
        this.topo.pretraitementShapes(); 
        
    }//GEN-LAST:event_jButton12MouseClicked

    /**
    * Clic sur le bouton "Préparation des fichiers du réseau cuivre" (onglet "Module topologique")
    */
    private void jButton11MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton11MouseClicked
        boolean lireSR = jCheckBox11.isSelected();
        this.topo.pretraitementReseauCuivre(lireSR);
        
    }//GEN-LAST:event_jButton11MouseClicked

    /**
    * Clic sur le bouton "Regroupement des NRA en NRO et création des shapefiles des ZANRO"
    */
    private void jButton8MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton8MouseClicked
        boolean lireSR = jCheckBox11.isSelected();
		List<String> listeDpts = splitAll(jList1.getSelectedValuesList(), " | ", 0);
        int nbLignesMinNRO = Integer.parseInt(jTextField22.getText());
        double distMaxNRONRA = Double.parseDouble(jTextField24.getText().replace(",", ".")); // en km
        this.topo.regrouperNRANRO(listeDpts, nbLignesMinNRO, distMaxNRONRA, lireSR);
    }//GEN-LAST:event_jButton8MouseClicked
    
    /**
    * Clic sur le bouton "Module topo et sortie des fichiers intermédiaires « BLO »" (onglet "Module topologique")
    */
    private void jButton5MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton5MouseClicked
        
        File dir = new File(cheminReseau);
        ArrayList<String> nroPossibles = new ArrayList<>();
        for (File f : dir.listFiles()){
            if (f.getName().startsWith("NRO"))
                nroPossibles.add(f.getName());
        }
        if (nroPossibles.isEmpty()){
            JOptionPane.showMessageDialog(null, "Le regroupement des NRA en NRO n'a pas eu lieu, veuillez effectuer cette étape avant de lancer le module topologique !");
        }else{
            Object[] possibleValues = nroPossibles.toArray();//{ "First", "Second", "Third" };
            String jeuNRO = (String) JOptionPane.showInputDialog(null,"Il existe "+possibleValues.length+" jeux de NRO disponibles.\nVeuillez choisir celui que vous souhaitez utiliser dans la liste déroulante ci-dessous :", "Input",JOptionPane.INFORMATION_MESSAGE, null, possibleValues, possibleValues[0]);
            if (jeuNRO == null){
                JOptionPane.showMessageDialog(null, "Vous devez choisir un jeu de NRO pour lancer le module topologique !");
            }else{
                System.out.println("Lancement du module topo avec le jeu de NRO "+jeuNRO);
                double toleranceNoeud = Double.parseDouble(jTextField16.getText().replace(",", "."));
                this.parametresReseau.put("toleranceNoeud", toleranceNoeud);
                
                double seuilToleranceGC2 = Double.parseDouble(jTextField20.getText().replace(",", "."));
                this.parametresReseau.put("seuilToleranceGC2", seuilToleranceGC2);
                
                boolean limitrophes = jCheckBox12.isSelected();
                boolean gc = jCheckBox13.isSelected();
                boolean routes = jCheckBox16.isSelected();
                boolean tracerShpReseau = jCheckBox17.isSelected();
                List<String> dptChoisis = splitAll(jList1.getSelectedValuesList(), " | ", 0);
                int reponse = JOptionPane.showConfirmDialog(this, "Préparation des graphes en tâche de fond - Cette opération va durer plusieurs heures\nContinuer ?", "Préparation des graphes", JOptionPane.YES_NO_OPTION);
                if (reponse == JOptionPane.YES_OPTION) {
                    System.out.println("Valeur du champ gc : "+gc);
                    this.topo.traceReseau(limitrophes, gc, routes, tracerShpReseau, jeuNRO, dptChoisis, this.parametresReseau);
                } else {
                    JOptionPane.showMessageDialog(this, "Préparation des graphes annulée");
                }
            }
        }
    }//GEN-LAST:event_jButton5MouseClicked

    /**
    * Clic sur le bouton "Création des shapefiles des ZANRA" (onglet "Module topologique")
    */
    private void jButton13MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton13MouseClicked
        
        boolean lireSR = jCheckBox11.isSelected();
        boolean suppression = jCheckBox5.isSelected();
        this.topo.createZAPCSRNRA(lireSR, suppression);
        
    }//GEN-LAST:event_jButton13MouseClicked

    /**
    * Clic sur le bouton "Mettre les départements à jour" (onglet "Choix des départements")
    */
    private void jButton14MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton14MouseClicked

        File dirGC = new File(adresseShapesGC);
        File dirRoutes = new File(adresseShapesRoutes);
        ArrayList<String> dptGC = new ArrayList<>();
        PrintWriter listeDpts = null;
        try{
            if (dirGC.exists() || dirRoutes.exists()){
                listeDpts = new PrintWriter(cheminReseau+fileDpts, "utf-8");
                listeDpts.println("Departement;Reseau");
            }
            if (dirGC.exists()) {
                File[] fl = dirGC.listFiles();
                for (File f : fl) {
                    String dpt = f.getName();
                    dptGC.add(dpt);
                    listeDpts.println(dpt+";"+"GC");
                }
            } else System.out.println("Pas de fichiers de forme GC sur le disque local à l'emplacement prévu !");
            if (dirRoutes.exists()){
                File[] fl = dirRoutes.listFiles();
                for (File file : fl) {
                    String dpt = file.getName();
                        if (!dptGC.contains(dpt))
                            listeDpts.println(dpt+";"+"routier");
                }                
            } else System.out.println("Pas de fichiers de forme routes sur le disque local à l'emplacement prévu !");
            if (dirGC.exists() || dirRoutes.exists()){
                listeDpts.close();
                initListesDpts();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        
    }//GEN-LAST:event_jButton14MouseClicked

    /**
    * Clic sur le bouton "Pré-traitement réseau de collecte" (onglet "Module topologique")
    */
    private void jButton15MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton15MouseClicked
        
        this.topo.pretraitementCollecte();
        
    }//GEN-LAST:event_jButton15MouseClicked

    /**
    * Clic sur le bouton "lancement de la modélisation" (onglet "Module topologique")
    */
    private void jButton3MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jButton3MouseClicked

        File dir = new File(cheminReseau);
        ArrayList<String> bloPossibles = new ArrayList<>();
        for (File f : dir.listFiles()){
            if (f.getName().startsWith("BLO"))
            bloPossibles.add(f.getName());
        }
        if (bloPossibles.isEmpty()){
            JOptionPane.showMessageDialog(null, "Aucun tracé n'est disponible, veuillez lancer le module topologique avant d'utiliser le module de déploiement");
        }else{
            Object[] possibleValues = bloPossibles.toArray();
            String traceSelectionne = (String) JOptionPane.showInputDialog(null,"Il existe "+possibleValues.length+" tracés disponibles.\nVeuillez choisir celui que vous souhaitez utiliser dans la liste déroulante ci-dessous :", "Input",JOptionPane.INFORMATION_MESSAGE, null, possibleValues, possibleValues[0]);
            if (traceSelectionne == null){
                JOptionPane.showMessageDialog(null, "Vous devez choisir un tracé de réseau pour lancer le module de déploiement !");
            }else{
                System.out.println("Lancement du modèle avec le tracé "+traceSelectionne);

                //Listing des départements et des zones
                List<String> dptsChoisis = splitAll(jList1.getSelectedValuesList(), " | ", 0);
                Parametres parametres = new Parametres();
                parametres.setZones(jCheckBox9.isSelected(), jCheckBox8.isSelected(), jCheckBox7.isSelected());
                parametres.setListeCommunes(this.dossierCommunes, jCheckBox6.isSelected(), "ListeSpeciale.csv", dptsChoisis);
                parametres.setDossierResultats(racineResultats, jCheckBox9.isSelected(), jCheckBox8.isSelected(), jCheckBox7.isSelected(), traceSelectionne);
                parametres.initDemande(dptsChoisis);
                for (String fichierDemandeCible : this.demandeCible){
                    parametres.addDemande(fichierDemandeCible);
                }
                //Copie des paramètres
                parametres.setPM(Integer.parseInt(jTextField1.getText()), Integer.parseInt(jTextField5.getText()), this.jRadioButton1.isSelected(),
                    Double.parseDouble(jTextField2.getText().replace(",", ".")), Integer.parseInt(jTextField4.getText()), Integer.parseInt(jTextField3.getText()));

                parametres.setTransportOptique(Double.parseDouble(jTextField9.getText().replace("%", "").replace(",", ".")) / 100,
                    Integer.parseInt(jTextField10.getText()), Integer.parseInt(jTextField19.getText()));

                parametres.setCoupleurs(); // les valeurs des paramètres techniques conernant les coupleurs sont indiquées en dur dans la définition de cette méthode dans la classe Parametres

                int tailleMaxSouterrain = Integer.parseInt(((String) jComboBox1.getSelectedItem()).replace(" FO", ""));;
                int tailleMaxAerien = Integer.parseInt(((String) jComboBox2.getSelectedItem()).replace(" FO", ""));;
                int tailleMinHorizontal = Integer.parseInt(((String) jComboBox3.getSelectedItem()).replace(" FO", ""));;
                System.out.println("Taille max souterrain : "+tailleMaxSouterrain);
                System.out.println("Taille max aérien : "+tailleMaxAerien);
                System.out.println("Taille min horizontal : "+tailleMinHorizontal);
                parametres.setCalibresLimites(tailleMaxSouterrain, tailleMaxAerien, tailleMinHorizontal);

                // paramètres de calcul des UO
                double facteurSurcapaciteDistri = 1 + Double.parseDouble(jTextField11.getText().replace("%", "").replace(",", ".")) / 100;
                double facteurSurcapaciteTransport = 1 + Double.parseDouble(jTextField12.getText().replace("%", "").replace(",", ".")) / 100;
                double longFibreSupp = 1 + Double.parseDouble(jTextField21.getText().replace("%", "").replace(",", ".")) / 100;
                double seuil_boitier = Integer.parseInt(jTextField18.getText().replace(",", "."));
                parametres.setUO(facteurSurcapaciteDistri, facteurSurcapaciteTransport, longFibreSupp, seuil_boitier);

                //double partReconstrPleineTerreAerien = Double.parseDouble(jTextField14.getText().replace("%", "").replace(",", ".")) / 100;
                //parametres.setGC(partReconstrPleineTerreAerien);
                parametres.setModeReconstructionGC((String) jComboBox4.getSelectedItem(), (String) jComboBox5.getSelectedItem());

                parametres.setNbMaxLignesPM(Integer.parseInt(jTextField25.getText()), Integer.parseInt(jTextField26.getText()), Integer.parseInt(jTextField27.getText()), Integer.parseInt(jTextField28.getText()));
                parametres.nbMaxLignesPBO = Integer.parseInt(jTextField29.getText());

                int nbFibresParTiroir = Integer.parseInt(jTextField30.getText());
                int nbTiroirsParRTO = Integer.parseInt(jTextField31.getText());
                double surfaceBaie = Double.parseDouble(jTextField32.getText().replace(",", "."));
                double facteurSurface = Double.parseDouble(jTextField35.getText().replace(",", "."));;

                parametres.setDimensionnementNRO(nbFibresParTiroir, nbTiroirsParRTO, surfaceBaie, facteurSurface);

                parametres.setSorties(jCheckBox1.isSelected(), jCheckBox14.isSelected(), jCheckBox2.isSelected(), jCheckBox15.isSelected(), jCheckBox3.isSelected(), jCheckBox4.isSelected(), jCheckBox10.isSelected());

                CoutsUnitaires couts = new CoutsUnitaires();
                couts.initCouts(jTable1);

                parametres.print(traceSelectionne, this.demandeCible);
                Deploiement deploiement = new Deploiement(parametres, couts, dptsChoisis, "", "", "NRO"+traceSelectionne.replace("BLO", "").replace("-routier","").replace("-GC",""), traceSelectionne, fichierImmeubles);

                FenProgression fp = new FenProgression(this);
                deploiement.fen = fp;

                fp.setVisible(true);
                this.setVisible(false);

                (MonitoredThread.fromRunnable("Déploiement : Lancer la modélisation", deploiement)).start();

            }
        }
    }//GEN-LAST:event_jButton3MouseClicked

    private void jComboBox4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox4ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jComboBox4ActionPerformed

    private List<String> splitAll(List<String> liste, String separateur, int indice){
        List<String> res = new ArrayList<>();
        for (String s : liste){
            res.add(s.split(separateur)[indice]);
        }
        return res;
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.ButtonGroup buttonGroup3;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton11;
    private javax.swing.JButton jButton12;
    private javax.swing.JButton jButton13;
    private javax.swing.JButton jButton14;
    private javax.swing.JButton jButton15;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton5;
    private javax.swing.JButton jButton6;
    private javax.swing.JButton jButton7;
    private javax.swing.JButton jButton8;
    private javax.swing.JCheckBox jCheckBox1;
    private javax.swing.JCheckBox jCheckBox10;
    private javax.swing.JCheckBox jCheckBox11;
    private javax.swing.JCheckBox jCheckBox12;
    private javax.swing.JCheckBox jCheckBox13;
    private javax.swing.JCheckBox jCheckBox14;
    private javax.swing.JCheckBox jCheckBox15;
    private javax.swing.JCheckBox jCheckBox16;
    private javax.swing.JCheckBox jCheckBox17;
    private javax.swing.JCheckBox jCheckBox2;
    private javax.swing.JCheckBox jCheckBox3;
    private javax.swing.JCheckBox jCheckBox4;
    private javax.swing.JCheckBox jCheckBox5;
    private javax.swing.JCheckBox jCheckBox6;
    private javax.swing.JCheckBox jCheckBox7;
    private javax.swing.JCheckBox jCheckBox8;
    private javax.swing.JCheckBox jCheckBox9;
    private javax.swing.JComboBox jComboBox1;
    private javax.swing.JComboBox jComboBox2;
    private javax.swing.JComboBox jComboBox3;
    private javax.swing.JComboBox<String> jComboBox4;
    private javax.swing.JComboBox<String> jComboBox5;
    private javax.swing.JEditorPane jEditorPane1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel27;
    private javax.swing.JLabel jLabel28;
    private javax.swing.JLabel jLabel29;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel30;
    private javax.swing.JLabel jLabel31;
    private javax.swing.JLabel jLabel32;
    private javax.swing.JLabel jLabel33;
    private javax.swing.JLabel jLabel34;
    private javax.swing.JLabel jLabel35;
    private javax.swing.JLabel jLabel36;
    private javax.swing.JLabel jLabel37;
    private javax.swing.JLabel jLabel38;
    private javax.swing.JLabel jLabel39;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel40;
    private javax.swing.JLabel jLabel41;
    private javax.swing.JLabel jLabel42;
    private javax.swing.JLabel jLabel43;
    private javax.swing.JLabel jLabel44;
    private javax.swing.JLabel jLabel45;
    private javax.swing.JLabel jLabel46;
    private javax.swing.JLabel jLabel48;
    private javax.swing.JLabel jLabel49;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JList jList1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JRadioButton jRadioButton1;
    private javax.swing.JRadioButton jRadioButton2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField jTextField10;
    private javax.swing.JTextField jTextField11;
    private javax.swing.JTextField jTextField12;
    private javax.swing.JTextField jTextField16;
    private javax.swing.JTextField jTextField18;
    private javax.swing.JTextField jTextField19;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JTextField jTextField20;
    private javax.swing.JTextField jTextField21;
    private javax.swing.JTextField jTextField22;
    private javax.swing.JTextField jTextField24;
    private javax.swing.JTextField jTextField25;
    private javax.swing.JTextField jTextField26;
    private javax.swing.JTextField jTextField27;
    private javax.swing.JTextField jTextField28;
    private javax.swing.JTextField jTextField29;
    private javax.swing.JTextField jTextField3;
    private javax.swing.JTextField jTextField30;
    private javax.swing.JTextField jTextField31;
    private javax.swing.JTextField jTextField32;
    private javax.swing.JTextField jTextField35;
    private javax.swing.JTextField jTextField4;
    private javax.swing.JTextField jTextField5;
    private javax.swing.JTextField jTextField9;
    // End of variables declaration//GEN-END:variables
}
