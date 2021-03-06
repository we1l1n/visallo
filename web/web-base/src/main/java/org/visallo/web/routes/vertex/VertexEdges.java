package org.visallo.web.routes.vertex;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.v5analytics.webster.HandlerChain;
import org.vertexium.*;
import org.visallo.core.config.Configuration;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.trace.Trace;
import org.visallo.core.trace.TraceSpan;
import org.visallo.core.user.User;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.web.BaseRequestHandler;
import org.visallo.web.clientapi.model.ClientApiVertex;
import org.visallo.web.clientapi.model.ClientApiVertexEdges;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

public class VertexEdges extends BaseRequestHandler {
    private final Graph graph;

    @Inject
    public VertexEdges(
            final Graph graph,
            final UserRepository userRepository,
            final WorkspaceRepository workspaceRepository,
            final Configuration configuration) {
        super(userRepository, workspaceRepository, configuration);
        this.graph = graph;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        User user = getUser(request);
        Authorizations authorizations = getAuthorizations(request, user);
        String workspaceId = getActiveWorkspaceId(request);

        String graphVertexId = getAttributeString(request, "graphVertexId");
        int offset = getOptionalParameterInt(request, "offset", 0);
        int size = getOptionalParameterInt(request, "size", 25);
        String edgeLabel = getOptionalParameter(request, "edgeLabel");
        String relatedVertexId = getOptionalParameter(request, "relatedVertexId");

        Vertex vertex;
        Vertex relatedVertex = null;
        try (TraceSpan trace = Trace.start("getOriginalVertex").data("graphVertexId", graphVertexId)) {
            vertex = graph.getVertex(graphVertexId, authorizations);
            if (vertex == null) {
                respondWithNotFound(response);
                return;
            }
        }

        List<String> edgeIds;
        if (edgeLabel == null && relatedVertexId == null) {
            edgeIds = Lists.newArrayList(vertex.getEdgeIds(Direction.BOTH, authorizations));
        } else if (relatedVertexId == null){
            edgeIds = Lists.newArrayList(vertex.getEdgeIds(Direction.BOTH, edgeLabel, authorizations));
        } else {
            relatedVertex = graph.getVertex(relatedVertexId, authorizations);
            if (relatedVertex == null) {
                respondWithNotFound(response);
                return;
            }

            if (edgeLabel == null) {
                edgeIds = Lists.newArrayList(vertex.getEdgeIds(relatedVertex, Direction.BOTH, authorizations));
            } else {
                edgeIds = Lists.newArrayList(vertex.getEdgeIds(relatedVertex, Direction.BOTH, edgeLabel, authorizations));
            }
        }
        int totalEdgeCount = edgeIds.size();

        ClientApiVertexEdges result = new ClientApiVertexEdges();

        edgeIds = edgeIds.subList(Math.min(edgeIds.size(), offset), Math.min(edgeIds.size(), offset + size));
        List<Edge> edges = Lists.newArrayList(graph.getEdges(edgeIds, authorizations));
        Map<String, Vertex> vertices;
        try (TraceSpan trace = Trace.start("getConnectedVertices").data("graphVertexId", vertex.getId())) {
            vertices = getVertices(vertex.getId(), edges, authorizations);
        }

        for (Edge edge : edges) {
            String otherVertexId = relatedVertexId == null ? edge.getOtherVertexId(vertex.getId()) : relatedVertexId;
            Vertex otherVertex = relatedVertex == null ? vertices.get(otherVertexId) : relatedVertex;

            ClientApiVertexEdges.Edge clientApiEdge = new ClientApiVertexEdges.Edge();
            clientApiEdge.setRelationship(ClientApiConverter.toClientApiEdge(edge, workspaceId));
            ClientApiVertex clientApiVertex;
            if (otherVertex == null) {
                clientApiVertex = new ClientApiVertex();
                clientApiVertex.setId(otherVertexId);
            } else {
                clientApiVertex = ClientApiConverter.toClientApiVertex(otherVertex, workspaceId, authorizations);
            }
            clientApiEdge.setVertex(clientApiVertex);
            result.getRelationships().add(clientApiEdge);
        }
        result.setTotalReferences(totalEdgeCount);

        respondWithClientApiObject(response, result);
    }

    private Map<String, Vertex> getVertices(final String myVertexId, List<Edge> edges, Authorizations authorizations) {
        Iterable<String> vertexIds = getOtherVertexIds(myVertexId, edges);
        Iterable<Vertex> vertices = graph.getVertices(vertexIds, ClientApiConverter.SEARCH_FETCH_HINTS, authorizations);
        vertices = Iterables.filter(vertices, Predicates.notNull());
        return Maps.uniqueIndex(vertices, new Function<Vertex, String>() {
            @Override
            public String apply(Vertex vertex) {
                return vertex.getId();
            }
        });
    }

    private Iterable<String> getOtherVertexIds(final String myVertexId, List<Edge> edges) {
        return Iterables.transform(
                edges,
                new Function<Edge, String>() {
                    @Override
                    public String apply(Edge edge) {
                        return edge.getOtherVertexId(myVertexId);
                    }
                });
    }
}
