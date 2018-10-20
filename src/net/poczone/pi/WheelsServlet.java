package net.poczone.pi;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/wheels")
public class WheelsServlet extends HttpServlet {
	private static final long serialVersionUID = -8755000481256248273L;

	private Wheels wheels;

	@Override
	public void init() throws ServletException {
		wheels = new Wheels();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		boolean success = wheels.add(req.getParameter("x"));
		resp.setStatus(success ? 200 : 500);
		resp.setContentType("text/plain");
		resp.setHeader("Access-Control-Allow-Origin", "https://poczone.net");
		resp.getOutputStream().print(success ? ":-)" : ":-(");
	}

	@Override
	public void destroy() {
		wheels.shutdown();
		wheels = null;
	}
}
