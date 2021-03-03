package com.company;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.UnsupportedTagException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws Exception{
        if (args.length != 1) {
            throw new IllegalArgumentException("You need to specify a valid directory");
        }

        String dir = args[0];
        Path mp3dir = Paths.get(dir);

        if (!Files.exists(mp3dir)) {
            throw new IllegalArgumentException("The directory does not exist: " + mp3dir);
        }

        List<Path> mp3List = new ArrayList<>();

        try (DirectoryStream<Path> paths = Files.newDirectoryStream(mp3dir, "*.mp3")) {
            paths.forEach(p -> {
                System.out.println("Found: " + p.getFileName().toString());
                mp3List.add(p);
            });
        }

        List<Song> songs = mp3List.stream().map(path -> {
            try {
                Mp3File mp3file = new Mp3File(path);
                ID3v2 id3 = mp3file.getId3v2Tag();
                return new Song(id3.getArtist(), id3.getYear(), id3.getAlbum(), id3.getTitle());
            } catch (IOException | UnsupportedTagException | InvalidDataException e){
                throw new IllegalStateException(e);
            }
        }).collect(Collectors.toList());

        try(Connection conn = DriverManager.getConnection("jdbc:h2:~/mydatabase;AUTO_SERVER=TRUE;INIT=runscript from './create.sql'")) {
            PreparedStatement st = conn.prepareStatement("insert into SONGS (artist, year, album, title) values (?, ?, ?, ?);");

            for (Song song : songs) {
                st.setString(1, song.getArtist());
                st.setString(2, song.getYear());
                st.setString(3, song.getAlbum());
                st.setString(4, song.getTitle());
                st.addBatch();
            }
            int[] updates = st.executeBatch();
            System.out.println("Inserted [=" + updates.length + "] records into the database");
        }

        // Setup server
        Server server = new Server(8080);

        ServletContextHandler context = new ServletContextHandler(
                ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.setResourceBase(System.getProperty("java.io.tmpdir"));
        server.setHandler(context);

        context.addServlet(SongServlet.class, "/songs");
        server.start();

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI("http://localhost:8080/songs"));
        }

    }

    public static class SongServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            StringBuilder builder = new StringBuilder();
            try (Connection conn = DriverManager.getConnection("jdbc:h2:~/mydatabase")) {

                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("select * from SONGS");

                while (rs.next()) {
                    builder.append("<tr class=\"table\">")
                            .append("<td>").append(rs.getString("year")).append("</td>")
                            .append("<td>").append(rs.getString("artist")).append("</td>")
                            .append("<td>").append(rs.getString("album")).append("</td>")
                            .append("<td>").append(rs.getString("title")).append("</td>")
                            .append("</tr>");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            String string = "<html><h1>Your Songs</h1><table><tr><th>Year</th><th>Artist</th><th>Album</th><th>Title</th></tr>" + builder.toString() + "</table></html>";
            resp.getWriter().write(string);
        }
    }

    public static class Song {
        private final String artist;
        private final String year;
        private final String album;
        private final String title;

        public Song(String artist, String year, String album, String title) {
            this.artist = artist;
            this.year = year;
            this.album = album;
            this.title = title;
        }

        public String getArtist() {
            return artist;
        }
        public String getYear() {
            return year;
        }
        public String getAlbum() {
            return album;
        }
        public String getTitle() {
            return title;
        }

    }


}


