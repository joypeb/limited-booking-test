import { forward } from "@/lib/proxy/forward";

type BackendRouteContext = {
  params: Promise<{ path: string[] }>;
};

function options() {
  return {
    baseUrl: process.env.BACKEND_BASE_URL ?? "http://127.0.0.1:8080",
    allowedPort: Number(process.env.CONSOLE_PORT ?? 3000),
  };
}

export async function GET(
  request: Request,
  { params }: BackendRouteContext,
): Promise<Response> {
  const { path } = await params;
  return forward(request, path, options());
}

export async function POST(
  request: Request,
  { params }: BackendRouteContext,
): Promise<Response> {
  const { path } = await params;
  return forward(request, path, options());
}

export async function OPTIONS(
  request: Request,
  context: BackendRouteContext,
): Promise<Response> {
  return GET(request, context);
}

export async function HEAD(
  request: Request,
  context: BackendRouteContext,
): Promise<Response> {
  const response = await GET(request, context);
  return new Response(null, { status: response.status, headers: response.headers });
}
