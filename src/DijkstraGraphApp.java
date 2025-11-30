import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class DijkstraGraphApp extends JFrame {
    // Graph data: 7 nodes with fixed coordinates for display
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();

    private final GraphPanel graphPanel;
    private final JTextArea logArea;
    private final JComboBox<String> cbSource;
    private final JComboBox<String> cbTarget;
    private final JLabel lblDistance;

    public DijkstraGraphApp() {
        setTitle("Dijkstra Graph Visualizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 640);
        setLocationRelativeTo(null);

        // Build sample graph
        buildSampleGraph();

        graphPanel = new GraphPanel(nodes, edges);
        graphPanel.setPreferredSize(new Dimension(620, 560));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        cbSource = new JComboBox<>(nodes.keySet().toArray(new String[0]));
        cbTarget = new JComboBox<>(nodes.keySet().toArray(new String[0]));
        lblDistance = new JLabel("Distance: -");

        JPanel right = new JPanel(new BorderLayout(8,8));
        right.setBorder(new EmptyBorder(8,8,8,8));
        JPanel controls = new JPanel();
        controls.setLayout(new GridLayout(0,1,6,6));

        JButton btnRun = new JButton("Run Dijkstra");
        btnRun.addActionListener(this::onRun);
        JButton btnSave = new JButton("Save to Oracle DB");
        btnSave.addActionListener(e -> onSave());

        controls.add(new JLabel("Source node:"));
        controls.add(cbSource);
        controls.add(new JLabel("Target node:"));
        controls.add(cbTarget);
        controls.add(btnRun);
        controls.add(lblDistance);
        controls.add(btnSave);

        right.add(controls, BorderLayout.NORTH);
        right.add(new JScrollPane(logArea), BorderLayout.CENTER);

        // top panel to show algorithm name and logo drawn
        JPanel top = new JPanel(new BorderLayout());
        JLabel algoLabel = new JLabel("Algorithm: Dijkstra (shortest paths)");
        algoLabel.setFont(algoLabel.getFont().deriveFont(Font.BOLD, 14f));
        top.add(algoLabel, BorderLayout.WEST);
        top.add(new LogoPanel(), BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(graphPanel, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);

        // Clicking nodes selects source/target quickly
        graphPanel.addNodeClickListener((nodeId, button)->{
            if(button==1) cbSource.setSelectedItem(nodeId);
            if(button==3) cbTarget.setSelectedItem(nodeId);
        });
    }

    private void onRun(ActionEvent ev) {
        String source = (String) cbSource.getSelectedItem();
        String target = (String) cbTarget.getSelectedItem();
        logArea.setText("");

        DijkstraResult result = Dijkstra.compute(nodes, edges, source);
        graphPanel.highlightPath(result.getPathTo(target));

        // Log steps into text area
        StringBuilder sb = new StringBuilder();
        sb.append("Run at: ").append(LocalDateTime.now()).append('\n');
        sb.append("Source: ").append(source).append("\n");
        sb.append("Target: ").append(target).append("\n");
        sb.append("Distance to target: ").append(result.getDistance(target)).append("\n");
        sb.append("Path: ").append(String.join(" -> ", result.getPathTo(target))).append("\n\n");

        sb.append("Node distances:\n");
        for(String id: nodes.keySet()){
            sb.append(String.format(" %s : %s\n", id, result.getDistance(id)));
        }
        sb.append('\n');
        sb.append("Detailed relax operations:\n");
        for(String step: result.getSteps()) sb.append(step).append('\n');

        logArea.setText(sb.toString());
        lblDistance.setText("Distance: " + result.getDistance(target));
    }

    private void onSave(){
        String source = (String) cbSource.getSelectedItem();
        String target = (String) cbTarget.getSelectedItem();
        String path = String.join("->", graphPanel.getHighlightedPath());
        double distance = graphPanel.getHighlightedDistance();
        String details = logArea.getText();

        try (DBHelper db = new DBHelper()){
            db.insertRun(source, target, path, distance, details);
            JOptionPane.showMessageDialog(this, "Saved to database successfully.");
        } catch (Exception ex){
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving to DB: "+ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void buildSampleGraph(){
        // Define 7 nodes with coordinates
        nodes.put("A", new Node("A", 120, 80));
        nodes.put("B", new Node("B", 280, 60));
        nodes.put("C", new Node("C", 440, 80));
        nodes.put("D", new Node("D", 200, 220));
        nodes.put("E", new Node("E", 360, 220));
        nodes.put("F", new Node("F", 80, 340));
        nodes.put("G", new Node("G", 440, 340));

        // Add undirected weighted edges (you can adjust weights)
        addEdge("A","B", 4);
        addEdge("A","D", 2);
        addEdge("B","C", 3);
        addEdge("B","D", 5);
        addEdge("C","E", 7);
        addEdge("D","E", 2);
        addEdge("D","F", 6);
        addEdge("E","G", 1);
        addEdge("F","G", 8);
        addEdge("B","E", 4);
    }

    private void addEdge(String a, String b, double w){
        edges.add(new Edge(nodes.get(a), nodes.get(b), w));
        edges.add(new Edge(nodes.get(b), nodes.get(a), w)); // undirected: add both directions
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DijkstraGraphApp app = new DijkstraGraphApp();
            app.setVisible(true);
        });
    }

    // ----- Inner classes: Node, Edge, GraphPanel, Dijkstra, DBHelper, LogoPanel -----

    static class Node {
        final String id; final int x,y;
        Node(String id,int x,int y){this.id=id;this.x=x;this.y=y;}
    }

    static class Edge {
        final Node from, to; final double weight;
        Edge(Node from, Node to, double weight){this.from=from;this.to=to;this.weight=weight;}
    }

    static class GraphPanel extends JPanel {
        private final Map<String, Node> nodes;
        private final List<Edge> edges;
        private List<String> highlightedPath = new ArrayList<>();
        private double highlightedDistance = Double.POSITIVE_INFINITY;
        private final int NODE_RADIUS = 18;
        private NodeClickListener clickListener;

        GraphPanel(Map<String, Node> nodes, List<Edge> edges){
            this.nodes = nodes; this.edges = edges;
            setBackground(Color.white);

            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e){
                    for(Node n: nodes.values()){
                        int dx = e.getX()-n.x; int dy = e.getY()-n.y;
                        if(dx*dx+dy*dy <= NODE_RADIUS*NODE_RADIUS){
                            if(clickListener!=null) clickListener.onNodeClicked(n.id, e.getButton());
                            break;
                        }
                    }
                }
            });
        }

        public void addNodeClickListener(NodeClickListener l){this.clickListener=l;}

        public void highlightPath(List<String> path){
            this.highlightedPath = (path==null)?new ArrayList<>():new ArrayList<>(path);
            // compute distance
            highlightedDistance = 0;
            for(int i=0;i+1<highlightedPath.size();i++){
                Node a = nodes.get(highlightedPath.get(i));
                Node b = nodes.get(highlightedPath.get(i+1));
                double w = findWeight(a,b);
                highlightedDistance += w;
            }
            repaint();
        }

        private double findWeight(Node a, Node b){
            for(Edge e: edges) if(e.from==a && e.to==b) return e.weight;
            return Double.POSITIVE_INFINITY;
        }

        public List<String> getHighlightedPath(){return highlightedPath;}
        public double getHighlightedDistance(){return highlightedDistance;}

        @Override
        protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // draw edges
            for(Edge e: edges){
                // draw only one direction visually (skip duplicates)
                if(e.from.id.compareTo(e.to.id) < 0){
                    boolean onPath = isEdgeOnHighlighted(e.from.id, e.to.id);
                    Stroke old = g2.getStroke();
                    g2.setStroke(new BasicStroke(onPath?4f:2f));
                    g2.setColor(onPath?Color.ORANGE:Color.GRAY);
                    g2.drawLine(e.from.x, e.from.y, e.to.x, e.to.y);

                    // draw weight label
                    int mx = (e.from.x + e.to.x)/2;
                    int my = (e.from.y + e.to.y)/2;
                    g2.setColor(Color.black);
                    g2.drawString(String.valueOf((int)e.weight), mx+4, my-4);
                    g2.setStroke(old);
                }
            }

            // draw nodes
            for(Node n: nodes.values()){
                boolean highlighted = highlightedPath.contains(n.id);
                g2.setColor(highlighted?Color.ORANGE:new Color(16,23,42));
                g2.fillOval(n.x-NODE_RADIUS/2, n.y-NODE_RADIUS/2, NODE_RADIUS, NODE_RADIUS);
                g2.setColor(Color.white);
                FontMetrics fm = g2.getFontMetrics();
                int w = fm.stringWidth(n.id);
                g2.drawString(n.id, n.x - w/2, n.y + fm.getAscent()/2 - 2);
            }
        }

        private boolean isEdgeOnHighlighted(String a, String b){
            for(int i=0;i+1<highlightedPath.size();i++){
                String x = highlightedPath.get(i);
                String y = highlightedPath.get(i+1);
                if((x.equals(a)&&y.equals(b)) || (x.equals(b)&&y.equals(a))) return true;
            }
            return false;
        }
    }

    interface NodeClickListener{ void onNodeClicked(String nodeId, int mouseButton); }

    static class DijkstraResult{
        private final Map<String, Double> dist = new HashMap<>();
        private final Map<String, String> prev = new HashMap<>();
        private final List<String> steps = new ArrayList<>();

        public void set(String node,double d){ dist.put(node,d); }
        public double getDistance(String node){ return dist.getOrDefault(node, Double.POSITIVE_INFINITY); }
        public void setPrev(String node,String p){ prev.put(node,p); }
        public List<String> getPathTo(String target){
            if(!dist.containsKey(target)) return Collections.emptyList();
            LinkedList<String> path = new LinkedList<>();
            String cur = target;
            while(cur!=null){ path.addFirst(cur); cur = prev.get(cur); }
            return path;
        }
        public void addStep(String s){ steps.add(s); }
        public List<String> getSteps(){ return steps; }
    }

    static class Dijkstra {
        // nodes: id->Node, edges: directed edges list
        public static DijkstraResult compute(Map<String, Node> nodes, List<Edge> edges, String source){
            DijkstraResult res = new DijkstraResult();
            Map<String, List<Edge>> adj = new HashMap<>();
            for(String id: nodes.keySet()) adj.put(id, new ArrayList<>());
            for(Edge e: edges){ adj.get(e.from.id).add(e); }

            for(String id: nodes.keySet()) res.set(id, Double.POSITIVE_INFINITY);
            res.set(source, 0);
            res.setPrev(source, null);

            PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingDouble(res::getDistance));
            pq.add(source);
            res.addStep("Init: set distance("+source+")=0 and others=INF");

            while(!pq.isEmpty()){
                String u = pq.poll();
                double du = res.getDistance(u);
                res.addStep("Extract min: " + u + " (dist=" + du + ")");
                for(Edge e: adj.get(u)){
                    String v = e.to.id;
                    double alt = du + e.weight;
                    res.addStep(String.format("Relax edge %s->%s (weight=%.1f): alt=%.1f, dist(%s)=%.1f", u, v, e.weight, alt, v, res.getDistance(v)));
                    if(alt < res.getDistance(v)){
                        res.set(v, alt);
                        res.setPrev(v, u);
                        pq.remove(v); // update priority
                        pq.add(v);
                        res.addStep(String.format("  Updated: dist(%s)=%.1f, prev(%s)=%s", v, alt, v, u));
                    }
                }
            }
            return res;
        }
    }

    // Simple DB helper that implements AutoCloseable
    static class DBHelper implements AutoCloseable {
        // TODO: configure these constants with your Oracle DB info
        private static final String CONNECTION_URL = "jdbc:oracle:thin:@//localhost:1521/orcl"; // host:port/service
        private static final String USER = "system";
        private static final String PASSWORD = "Tapiero123";

        private Connection conn;

        public DBHelper() throws SQLException {
            // ensure driver is available
            conn = DriverManager.getConnection(CONNECTION_URL, USER, PASSWORD);
        }

        public void insertRun(String source, String target, String path, double distance, String details) throws SQLException{
            String sql = "INSERT INTO DIJKSTRA_LOG (SOURCE_NODE, TARGET_NODE, PATH, DISTANCE, DETAILS) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)){
                ps.setString(1, source);
                ps.setString(2, target);
                ps.setString(3, path);
                ps.setDouble(4, distance);
                ps.setString(5, details);
                ps.executeUpdate();
            }
        }

        @Override
        public void close() throws SQLException { if(conn!=null) conn.close(); }
    }

    // LogoPanel draws a minimal logo (fallback if SVG not used)
    static class LogoPanel extends JPanel{
        LogoPanel(){ setPreferredSize(new Dimension(200,60)); }
        @Override
        protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // simple emblem
            g2.setColor(new Color(14,23,42));
            g2.fillOval(6,6,40,40);
            g2.setColor(new Color(14,181,178));
            g2.fillOval(22,18,8,8);
            g2.setColor(Color.BLACK);
            g2.drawString("Dijkstra", 60, 28);
            g2.setFont(g2.getFont().deriveFont(10f));
            g2.drawString("Graph Visualizer", 60, 45);
        }
    }
}