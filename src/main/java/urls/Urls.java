package urls;

import static sam.manga.samrock.urls.MangaUrlsMeta.MANGA_ID;
import static sam.manga.samrock.urls.MangaUrlsMeta.TABLE_NAME;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import sam.fx.clipboard.FxClipboard;
import sam.fx.helpers.FxButton;
import sam.fx.helpers.FxConstants;
import sam.fx.helpers.FxHBox;
import sam.fx.helpers.FxText;
import sam.fx.helpers.FxUtils;
import sam.manga.samrock.SamrockDB;

public class Urls extends Application {
	private SamrockDB db;
	private final Text count = new Text();
	private final BorderPane root = new BorderPane(new Text("nothing"));
	private final TableView<Wrap> table = new TableView<>();
	private Stage stage;
	private ObservableMap<Update, String> updates = FXCollections.observableHashMap();

	private static class Update {
		final String id;
		final int col;

		public Update(String id, int col) {
			this.id = id;
			this.col = col;
		}

		@Override
		public int hashCode() {
			return Objects.hash(col, id);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Update other = (Update) obj;
			return col == other.col && Objects.equals(id, other.id);
		}
	}

	private class Wrap {
		final String[] current;

		public Wrap(String[] current) {
			this.current = current;
		}

		public String get(int n) {
			return current[n];
		}

		public void set(int k, String newValue) {
			if(!Objects.equals(current[k], newValue)) {
				current[k] = newValue;
				updates.put(new Update(current[0], k), newValue);
			}
		}
	}

	@Override
	public void start(Stage stage) throws Exception {
		this.stage = stage;

		TextField ids = new TextField();
		Pattern pattern = Pattern.compile("\\s+");
		EventHandler<ActionEvent> handler = e -> load(pattern.splitAsStream(ids.getText()).collect(Collectors.toList()));
		HBox box = new HBox(5, new Text("ids"), ids, FxButton.button("OK", handler, ids.textProperty().isEmpty()), count);
		ids.setOnAction(handler);

		box.setPadding(FxConstants.INSETS_5);
		box.setAlignment(Pos.CENTER);
		HBox.setHgrow(ids, Priority.ALWAYS);
		ids.maxWidth(Double.MAX_VALUE);

		count.textProperty().bind(Bindings.size(table.getItems()).asString());

		root.setTop(box);
		BorderPane.setAlignment(count, Pos.CENTER_RIGHT);
		BorderPane.setMargin(count, FxConstants.INSETS_5);

		stage.setScene(new Scene(root));
		stage.setWidth(500);
		stage.setHeight(500);
		stage.show();

		ids.setText(String.join(" ", getParameters().getRaw()));
		load(new ArrayList<>(getParameters().getRaw()));

	}

	private List<String> prev_args;
	private String[] colums;

	private void load(List<String> args) {
		if(Objects.equals(prev_args, args))
			return;

		args.removeIf(s -> {
			if(!s.matches("\\d+")) {
				System.out.println("bad manga_id: "+s);
				return true;
			}
			return false;
		});

		if(args.isEmpty())
			root.setCenter(new Text("no manga_ids supplied"));
		else {
			if(root.getCenter() != table)
				root.setCenter(table);

			try {
				if(db == null) 
					this.db = new SamrockDB();

				ResultSet rs = db.executeQuery("select * from "+TABLE_NAME+" where "+MANGA_ID+" in ("+String.join(",", args)+");");
				init(rs);

				List<Wrap> list = table.getItems();
				list.clear();
				int size = table.getColumns().size();

				while(rs.next()) {
					String[] val = new String[size];
					for (int i = 1; i <= val.length; i++) 
						val[i - 1] = rs.getString(i);

					list.add(new Wrap(val));
				}

				System.out.println("loaded : "+args);
			} catch (Throwable e) {
				FxUtils.setErrorTa(stage, "error", null, e);
			}			
		}
	}



	private void init(ResultSet rs) throws SQLException {
		if(this.colums != null)
			return;

		ResultSetMetaData rsm = rs.getMetaData();
		int size = rsm.getColumnCount();

		this.colums = new String[size];
		for (int i = 1; i <= colums.length; i++) 
			colums[i - 1] = rsm.getColumnLabel(i);

		for (int i = 0; i < colums.length; i++) {
			int k = i;

			TableColumn<Wrap, String> t = new TableColumn<>(colums[k]);
			if(k != 0)
				t.setCellFactory(TextFieldTableCell.forTableColumn());

			t.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().get(k)) {
				@Override
				public void set(String newValue) {
					cell.getValue().set(k, newValue);
					super.set(newValue);
				}
			});
			table.getColumns().add(t);
		}

		table.setOnKeyReleased(e -> {
			if(e.isControlDown()) {
				switch (e.getCode()) {
					case C:
					{
						TablePosition<Wrap, String> pos = selectedCell();
						if(pos != null)
							FxClipboard.setString(pos.getTableColumn().getCellData(pos.getRow()));
					}
						break;
					case V:
						String s = FxClipboard.getString();
						if(s != null) {
							TablePosition<Wrap, String> pos = selectedCell();
							Object o = pos.getTableColumn().getCellObservableValue(pos.getRow());
							((SimpleStringProperty)o).set(s);
							table.refresh();
						}
						break;
					default:
						break;
				}
			}
		});

		table.setEditable(true);
		table.getSelectionModel().setCellSelectionEnabled(true);
		table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

		Button btn = FxButton.button("SAVE", e -> save(), Bindings.isEmpty(updates));
		HBox box = FxHBox.buttonBox(btn, FxText.of(Bindings.size(updates).asString()));
		root.setBottom(box);

	}
	
	@SuppressWarnings("unchecked")
	private TablePosition<Wrap, String> selectedCell() {
		return table.getFocusModel().getFocusedCell();
	}

	StringBuilder sb;
	String format;
	Formatter fm;

	private void save() {
		if(sb == null) {
			sb = new StringBuilder();
			format = "UPDATE "+TABLE_NAME+" SET %s='%s' WHERE "+MANGA_ID+"=%s;\n";
			fm = new Formatter(sb);
		}
		updates.forEach((u, val) -> fm.format(format, colums[u.col], val, u.id));

		String s = sb.toString();
		sb.setLength(0);

		System.out.println(s);
		try {
			System.out.println("executes: "+db.executeUpdate(s));
			updates.clear();
			db.commit();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}



	@Override
	public void stop() throws Exception {
		if(db != null)
			db.close();
	}

}
