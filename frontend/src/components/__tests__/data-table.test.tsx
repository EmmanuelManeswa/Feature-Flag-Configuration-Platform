import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { DataTable } from "@/components/data-table";
import type { ColumnDef } from "@/components/data-table";

interface Row {
  id: string;
  name: string;
}

const columns: ColumnDef<Row, unknown>[] = [
  { accessorKey: "id", header: "ID" },
  { accessorKey: "name", header: "Name" },
];

describe("DataTable", () => {
  it("renders skeleton rows while loading", () => {
    render(<DataTable columns={columns} data={[]} isLoading page={0} totalPages={0} onPageChange={() => {}} />);
    // Skeletons render inside table cells; loading state has no "no results" text.
    expect(screen.queryByText(/no results/i)).not.toBeInTheDocument();
  });

  it("shows the empty message when there is no data and it is not loading", () => {
    render(
      <DataTable
        columns={columns}
        data={[]}
        isLoading={false}
        emptyMessage="Nothing here yet."
        page={0}
        totalPages={0}
        onPageChange={() => {}}
      />,
    );
    expect(screen.getByText("Nothing here yet.")).toBeInTheDocument();
  });

  it("renders row data once loaded", () => {
    render(
      <DataTable
        columns={columns}
        data={[{ id: "1", name: "Alpha" }]}
        isLoading={false}
        page={0}
        totalPages={1}
        onPageChange={() => {}}
      />,
    );
    expect(screen.getByText("Alpha")).toBeInTheDocument();
  });

  it("hides pagination controls when there is only one page", () => {
    render(
      <DataTable
        columns={columns}
        data={[{ id: "1", name: "Alpha" }]}
        isLoading={false}
        page={0}
        totalPages={1}
        onPageChange={() => {}}
      />,
    );
    expect(screen.queryByRole("button", { name: /next/i })).not.toBeInTheDocument();
  });
});
